/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.domain.controller;

import java.io.InputStream;
import java.io.OutputStream;

import org.jboss.as.communication.SocketConnection;
import org.jboss.as.model.HostModel;
import org.jboss.logging.Logger;

/**
 * Responsible for managing the communication with a single server manager instance.
 *
 * @author John E. Bailey
 */
public class ServerManagerConnection implements Runnable {
    private static final Logger log = Logger.getLogger("org.jboss.as.domain.controller");

    private final String id;
    private final SocketConnection socketConnection;
    private final InputStream socketIn;
    private final OutputStream socketOut;
    private final ServerManagerCommunicationService communicationService;
    private final DomainController domainController;
    private HostModel hostConfig;


    /**
     * Create a new instance.
     *
     * @param id The server manager identifier
     * @param domainController The domain controller
     * @param communicationService The communication service
     * @param socketConnection The server managers socket
     */
    public ServerManagerConnection(final String id, final DomainController domainController, final ServerManagerCommunicationService communicationService, final SocketConnection socketConnection) {
        this.id = id;
        this.domainController = domainController;
        this.communicationService = communicationService;
        this.socketConnection = socketConnection;
        this.socketIn = socketConnection.getInputStream();
        this.socketOut = socketConnection.getOutputStream();
    }

    @Override
    public void run() {
        try {
            for (;;) {
                if(!socketConnection.isConnected())
                    break;
                ServerManagerConnectionProtocol.IncomingCommand.processNext(this, socketIn);
            }
        } catch (Throwable t) {
            log.error(t);
            throw new RuntimeException(t);
        } finally {
            socketConnection.close();
        }
    }

    public String getId() {
        return id;
    }

    public HostModel getHostConfig() {
        return hostConfig;
    }

    public void setHostConfig(HostModel hostConfig) {
        this.hostConfig = hostConfig;
    }

    public void updateDomain() {
        try {
            ServerManagerConnectionProtocol.OutgoingCommand.UPDATE_DOMAIN.execute(this, socketOut);
        } catch (Exception e) {
            throw new RuntimeException("Failed to update server manager with new domain", e); // TODO: Better exception
        }
    }

    void confirmRegistration() throws Exception {
        try {
            ServerManagerConnectionProtocol.OutgoingCommand.CONFIRM_REGISTRATION.execute(this, socketOut);
        } catch (Exception e) {
            throw new RuntimeException(e); // TODO: Better exception
        }
    }

    void unregistered() {
        communicationService.removeServerManagerConnection(this);
    }

    DomainController getDomainController() {
        return domainController;
    }
}
