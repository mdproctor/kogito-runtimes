/*
 * Copyright 2012 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.droolsjbpm.services.impl;

import java.util.Collection;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import org.droolsjbpm.services.api.RulesNotificationService;
import org.droolsjbpm.services.impl.model.RuleNotificationInstanceDesc;
import org.jboss.seam.transaction.Transactional;

/**
 *
 * @author salaboy
 */
@Transactional
@ApplicationScoped
public class RulesNotificationServiceImpl implements RulesNotificationService {
    
    @Inject
    private EntityManager em;
    
    
    
    
    @Override
    public void insertNotification(int sessionId, String notification) {
        em.persist(new RuleNotificationInstanceDesc(sessionId, notification));
    }

    @Override
    public Collection<RuleNotificationInstanceDesc> getAllNotificationInstance() {
        List<RuleNotificationInstanceDesc> notifications = em.createQuery("select ni FROM RuleNotificationInstanceDesc ni  ORDER BY ni.dataTimeStamp DESC").getResultList();

        return notifications;
    }

    @Override
    public Collection<RuleNotificationInstanceDesc> getAllNotificationInstanceBySessionId(int sessionId) {
        List<RuleNotificationInstanceDesc> notifications = em.createQuery("select ni FROM RuleNotificationInstanceDesc ni where "
                + "ni.sessionId=:sessionId ORDER BY ni.dataTimeStamp DESC").setParameter("sessionId", sessionId)
                .getResultList();
        return notifications;
    }
    
}
