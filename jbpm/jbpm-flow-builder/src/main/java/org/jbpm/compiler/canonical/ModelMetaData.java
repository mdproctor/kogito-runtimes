/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jbpm.compiler.canonical;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jbpm.process.core.context.variable.Variable;
import org.kie.kogito.codegen.Generated;
import org.kie.kogito.codegen.VariableInfo;
import org.kie.kogito.internal.process.runtime.KogitoWorkflowProcess;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import static com.github.javaparser.StaticJavaParser.parse;
import static org.drools.util.StringUtils.ucFirst;

public class ModelMetaData {

    private final String processId;
    private final String packageName;
    private final String modelClassSimpleName;
    private final VariableDeclarations variableScope;
    private String modelClassName;
    private String visibility;
    private boolean hidden;
    private String templateName;
    private Consumer<CompilationUnit>[] customGenerator;

    private boolean supportsValidation;
    private boolean supportsOpenApiGeneration;

    private String modelSchemaRef;

    public ModelMetaData(String processId, String packageName, String modelClassSimpleName, String visibility, VariableDeclarations variableScope, boolean hidden) {
        this(processId, packageName, modelClassSimpleName, visibility, variableScope, hidden, "/class-templates/ModelTemplate.java");
    }

    public ModelMetaData(String processId, String packageName, String modelClassSimpleName, String visibility, VariableDeclarations variableScope, boolean hidden, String templateName) {
        this(processId, packageName, modelClassSimpleName, visibility, variableScope, hidden, templateName, c -> {
        });
    }

    public ModelMetaData(String processId, String packageName, String modelClassSimpleName, String visibility, VariableDeclarations variableScope, boolean hidden, String templateName,
            Consumer<CompilationUnit>... customGenerator) {
        this.processId = processId;
        this.packageName = packageName;
        this.modelClassSimpleName = modelClassSimpleName;
        this.variableScope = variableScope;
        this.modelClassName = packageName + '.' + modelClassSimpleName;
        this.visibility = visibility;
        this.hidden = hidden;
        this.templateName = templateName;
        this.customGenerator = customGenerator;
    }

    public String generate() {
        CompilationUnit modelClass = compilationUnit();
        Arrays.stream(customGenerator).forEach(generator -> generator.accept(modelClass));
        return modelClass.toString();
    }

    public AssignExpr newInstance(String assignVarName) {
        ClassOrInterfaceType type = new ClassOrInterfaceType(null, modelClassName);
        return new AssignExpr(
                new VariableDeclarationExpr(type, assignVarName),
                new ObjectCreationExpr().setType(type),
                AssignExpr.Operator.ASSIGN);
    }

    public MethodCallExpr fromMap(String variableName, String mapVarName) {
        return new MethodCallExpr(new NameExpr(variableName), "fromMap")
                .addArgument(new MethodCallExpr(new ThisExpr(), "id"))
                .addArgument(mapVarName);
    }

    public MethodCallExpr toMap(String varName) {
        return new MethodCallExpr(new NameExpr(varName), "toMap");
    }

    public BlockStmt copyInto(String sourceVarName, String destVarName, ModelMetaData dest, Map<String, String> mapping) {
        BlockStmt blockStmt = new BlockStmt();

        for (Map.Entry<String, String> e : mapping.entrySet()) {
            String destField = variableScope.getTypes().get(e.getKey()).getSanitizedName();
            String sourceField = e.getValue();
            blockStmt.addStatement(
                    dest.callSetter(destVarName, destField, dest.callGetter(sourceVarName, sourceField)));
        }

        return blockStmt;
    }

    public MethodCallExpr callSetter(String targetVar, String destField, String value) {
        if (value.startsWith("#{")) {
            value = value.substring(2, value.length() - 1);
        }

        return callSetter(targetVar, destField, new NameExpr(value));
    }

    public MethodCallExpr callUpdateFromMap(String targetVar, String mapVar) {
        return new MethodCallExpr(new NameExpr(targetVar), "update").addArgument(new NameExpr(mapVar));
    }

    public MethodCallExpr callSetter(String targetVar, String destField, Expression value) {
        String name = variableScope.getTypes().get(destField).getSanitizedName();
        String type = variableScope.getType(destField);
        String setter = "set" + ucFirst(name); // todo cache FieldDeclarations in compilationUnit()
        return new MethodCallExpr(new NameExpr(targetVar), setter).addArgument(
                new CastExpr(
                        new ClassOrInterfaceType(null, type),
                        new EnclosedExpr(value)));
    }

    public MethodCallExpr callGetter(String targetVar, String field) {
        String getter = "get" + ucFirst(field); // todo cache FieldDeclarations in compilationUnit()
        return new MethodCallExpr(new NameExpr(targetVar), getter);
    }

    private CompilationUnit compilationUnit() {
        CompilationUnit compilationUnit = parse(this.getClass().getResourceAsStream(templateName));
        compilationUnit.setPackageDeclaration(packageName);
        Optional<ClassOrInterfaceDeclaration> processMethod = compilationUnit.findFirst(ClassOrInterfaceDeclaration.class, sl1 -> true);

        if (!processMethod.isPresent()) {
            throw new NoSuchElementException("Cannot find class declaration in the template");
        }
        ClassOrInterfaceDeclaration modelClass = processMethod.get();

        if (!KogitoWorkflowProcess.PRIVATE_VISIBILITY.equals(visibility)) {
            modelClass.addAnnotation(new NormalAnnotationExpr(new Name(Generated.class.getCanonicalName()), NodeList.nodeList(new MemberValuePair("value", new StringLiteralExpr("kogito-codegen")),
                    new MemberValuePair("reference", new StringLiteralExpr(processId)),
                    new MemberValuePair("name", new StringLiteralExpr(ucFirst(ProcessToExecModelGenerator.extractProcessId(processId)))),
                    new MemberValuePair("hidden", new BooleanLiteralExpr(hidden)))));
        }
        modelClass.setName(modelClassSimpleName);

        // setup of the toMap method body
        BlockStmt toMapBody = new BlockStmt();
        ClassOrInterfaceType toMap = new ClassOrInterfaceType(null, new SimpleName(Map.class.getSimpleName()),
                NodeList.nodeList(new ClassOrInterfaceType(null, String.class.getSimpleName()), new ClassOrInterfaceType(null, Object.class.getSimpleName())));
        VariableDeclarationExpr paramsField = new VariableDeclarationExpr(toMap, "params");
        toMapBody.addStatement(
                new AssignExpr(paramsField, new ObjectCreationExpr(null, new ClassOrInterfaceType(null,
                        HashMap.class.getSimpleName() + "<>"), NodeList.nodeList()), AssignExpr.Operator.ASSIGN));

        // setup of static fromMap method body        
        BlockStmt staticFromMap = new BlockStmt();

        if (modelClass.findFirst(MethodDeclaration.class, md -> md.getNameAsString().equals("getId")).isPresent()) {
            FieldAccessExpr idField = new FieldAccessExpr(new ThisExpr(), "id");
            staticFromMap.addStatement(new AssignExpr(idField, new NameExpr("id"), AssignExpr.Operator.ASSIGN));
        }
        for (Map.Entry<String, Variable> variable : variableScope.getTypes().entrySet()) {
            String varName = variable.getValue().getName();
            String vtype = variable.getValue().getType().getStringType();
            String sanitizedName = variable.getValue().getSanitizedName();

            FieldDeclaration fd = declareField(sanitizedName, vtype);
            modelClass.addMember(fd);

            List<String> tags = variable.getValue().getTags();
            fd.addAnnotation(new NormalAnnotationExpr(new Name(VariableInfo.class.getCanonicalName()),
                    NodeList.nodeList(new MemberValuePair("tags", new StringLiteralExpr(String.join(",", tags))))));
            fd.addAnnotation(new NormalAnnotationExpr(new Name(JsonProperty.class.getCanonicalName()),
                    NodeList.nodeList(new MemberValuePair("value",
                            new StringLiteralExpr(varName)))));

            applyValidation(fd, tags);
            applyOpenApiSchemaForJsonNodeModel(fd);
            applyOpenApiSchemaAnnotation(fd);

            fd.createGetter();
            fd.createSetter();

        }

        Optional<MethodDeclaration> toMapMethod = modelClass.findFirst(MethodDeclaration.class, sl -> sl.getName().asString().equals("toMap"));

        toMapBody.addStatement(new ReturnStmt(new NameExpr("params")));
        toMapMethod.ifPresent(methodDeclaration -> methodDeclaration.setBody(toMapBody));

        return compilationUnit;
    }

    private void applyValidation(FieldDeclaration fd, List<String> tags) {

        if (supportsValidation) {
            fd.addAnnotation("javax.validation.Valid");

            if (tags != null && tags.contains(Variable.REQUIRED_TAG)) {
                fd.addAnnotation("javax.validation.constraints.NotNull");
            }
        }
    }

    /**
     * The JsonNode class can't be parsed by the OpenApi parser.
     * In essence, JsonNode should be represented by a generic JSON node: `{}`.
     * The Swagger parser will handle a Java Object class as this generic Json node.
     * This method will apply the `Schema(class=Object.class)` annotation if the model type os a JsonNode type.
     *
     * @see <a href="https://github.com/smallrye/smallrye-open-api/issues/1048">Jackson's JsonNode class is being incorrectly rendered in the spec file</a>
     */
    private void applyOpenApiSchemaForJsonNodeModel(final FieldDeclaration modelFieldDeclaration) {
        if (this.modelSchemaRef == null &&
                this.supportsOpenApiGeneration &&
                JsonNode.class.getCanonicalName().equals(modelFieldDeclaration.getElementType().asString())) {
            modelFieldDeclaration.addAndGetAnnotation(Schema.class).addPair("implementation",
                    new ClassExpr().setType(Object.class.getCanonicalName()));
        }
    }

    private void applyOpenApiSchemaAnnotation(final FieldDeclaration modelFieldDeclaration) {
        if (this.supportsOpenApiGeneration && this.modelSchemaRef != null) {
            final NormalAnnotationExpr schemaAnnotation = new NormalAnnotationExpr();
            schemaAnnotation.setName(new Name(Schema.class.getCanonicalName()));
            schemaAnnotation.addPair("ref", new StringLiteralExpr(this.modelSchemaRef));
            modelFieldDeclaration.addAnnotation(schemaAnnotation);
        }
    }

    private FieldDeclaration declareField(String name, String type) {
        return new FieldDeclaration().addVariable(
                new VariableDeclarator()
                        .setType(type)
                        .setName(name))
                .addModifier(Modifier.Keyword.PRIVATE);
    }

    public String getModelClassSimpleName() {
        return modelClassSimpleName;
    }

    public String getModelClassName() {
        return modelClassName;
    }

    public boolean isSupportsValidation() {
        return supportsValidation;
    }

    public void setSupportsValidation(boolean supportsValidation) {
        this.supportsValidation = supportsValidation;
    }

    public boolean isSupportsOpenApiGeneration() {
        return supportsOpenApiGeneration;
    }

    public void setSupportsOpenApiGeneration(boolean supportsOpenApiGeneration) {
        this.supportsOpenApiGeneration = supportsOpenApiGeneration;
    }

    public void setModelSchemaRef(String modelSchemaRef) {
        this.modelSchemaRef = modelSchemaRef;
    }

    @Override
    public String toString() {
        return "ModelMetaData [modelClassName=" + modelClassName + "]";
    }
}
