/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.ha.session;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Session;
import org.apache.catalina.ha.ClusterManager;
import org.apache.catalina.ha.ClusterMessage;
import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.io.ReplicationStream;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;

/**
 * The DeltaManager manages replicated sessions by only replicating the deltas in data. For applications written to
 * handle this, the DeltaManager is the optimal way of replicating data.
 * <p>
 * This code is almost identical to StandardManager with a difference in how it persists sessions and some modifications
 * to it.
 * <p>
 * <b>IMPLEMENTATION NOTE </b>: Correct behavior of session storing and reloading depends upon external calls to the
 * <code>start()</code> and <code>stop()</code> methods of this class at the correct times.
 *
 * @author Craig R. McClanahan
 * @author Peter Rossbach
 */
public class DeltaManager extends ClusterManagerBase {

    // ---------------------------------------------------- Security Classes
    public final Log log = LogFactory.getLog(DeltaManager.class);

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(DeltaManager.class);

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    // ----------------------------------------------------- Instance Variables

    protected String name = null;

    private boolean expireSessionsOnShutdown = false;
    private boolean notifySessionListenersOnReplication = true;
    private boolean notifyContainerListenersOnReplication = true;
    private volatile boolean stateTransferred = false;
    private volatile boolean noContextManagerReceived = false;
    private int stateTransferTimeout = 60;
    private boolean sendAllSessions = true;
    private int sendAllSessionsSize = 1000;
    private boolean enableStatistics = true;

    /**
     * wait time between send session block (default 2 sec)
     */
    private int sendAllSessionsWaitTime = 2 * 1000;
    private final ArrayList<SessionMessage> receivedMessageQueue = new ArrayList<>();
    private boolean receiverQueue = false;
    private boolean stateTimestampDrop = true;
    private volatile long stateTransferCreateSendTime;

    // -------------------------------------------------------- stats attributes

    private final AtomicLong sessionReplaceCounter = new AtomicLong(0);
    private final AtomicLong counterReceive_EVT_GET_ALL_SESSIONS = new AtomicLong(0);
    private final AtomicLong counterReceive_EVT_ALL_SESSION_DATA = new AtomicLong(0);
    private final AtomicLong counterReceive_EVT_SESSION_CREATED = new AtomicLong(0);
    private final AtomicLong counterReceive_EVT_SESSION_EXPIRED = new AtomicLong(0);
    private final AtomicLong counterReceive_EVT_SESSION_ACCESSED = new AtomicLong(0);
    private final AtomicLong counterReceive_EVT_SESSION_DELTA = new AtomicLong(0);
    private final AtomicInteger counterReceive_EVT_ALL_SESSION_TRANSFERCOMPLETE = new AtomicInteger(0);
    private final AtomicLong counterReceive_EVT_CHANGE_SESSION_ID = new AtomicLong(0);
    private final AtomicLong counterReceive_EVT_ALL_SESSION_NOCONTEXTMANAGER = new AtomicLong(0);
    private final AtomicLong counterSend_EVT_GET_ALL_SESSIONS = new AtomicLong(0);
    private final AtomicLong counterSend_EVT_ALL_SESSION_DATA = new AtomicLong(0);
    private final AtomicLong counterSend_EVT_SESSION_CREATED = new AtomicLong(0);
    private final AtomicLong counterSend_EVT_SESSION_DELTA = new AtomicLong(0);
    private final AtomicLong counterSend_EVT_SESSION_ACCESSED = new AtomicLong(0);
    private final AtomicLong counterSend_EVT_SESSION_EXPIRED = new AtomicLong(0);
    private final AtomicInteger counterSend_EVT_ALL_SESSION_TRANSFERCOMPLETE = new AtomicInteger(0);
    private final AtomicLong counterSend_EVT_CHANGE_SESSION_ID = new AtomicLong(0);
    private final AtomicInteger counterNoStateTransferred = new AtomicInteger(0);


    // ------------------------------------------------------------- Constructor
    public DeltaManager() {
        super();
    }

    // ------------------------------------------------------------- Properties

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * @return Returns the counterSend_EVT_GET_ALL_SESSIONS.
     */
    public long getCounterSend_EVT_GET_ALL_SESSIONS() {
        return counterSend_EVT_GET_ALL_SESSIONS.get();
    }

    /**
     * @return Returns the counterSend_EVT_SESSION_ACCESSED.
     */
    public long getCounterSend_EVT_SESSION_ACCESSED() {
        return counterSend_EVT_SESSION_ACCESSED.get();
    }

    /**
     * @return Returns the counterSend_EVT_SESSION_CREATED.
     */
    public long getCounterSend_EVT_SESSION_CREATED() {
        return counterSend_EVT_SESSION_CREATED.get();
    }

    /**
     * @return Returns the counterSend_EVT_SESSION_DELTA.
     */
    public long getCounterSend_EVT_SESSION_DELTA() {
        return counterSend_EVT_SESSION_DELTA.get();
    }

    /**
     * @return Returns the counterSend_EVT_SESSION_EXPIRED.
     */
    public long getCounterSend_EVT_SESSION_EXPIRED() {
        return counterSend_EVT_SESSION_EXPIRED.get();
    }

    /**
     * @return Returns the counterSend_EVT_ALL_SESSION_DATA.
     */
    public long getCounterSend_EVT_ALL_SESSION_DATA() {
        return counterSend_EVT_ALL_SESSION_DATA.get();
    }

    /**
     * @return Returns the counterSend_EVT_ALL_SESSION_TRANSFERCOMPLETE.
     */
    public int getCounterSend_EVT_ALL_SESSION_TRANSFERCOMPLETE() {
        return counterSend_EVT_ALL_SESSION_TRANSFERCOMPLETE.get();
    }

    /**
     * @return Returns the counterSend_EVT_CHANGE_SESSION_ID.
     */
    public long getCounterSend_EVT_CHANGE_SESSION_ID() {
        return counterSend_EVT_CHANGE_SESSION_ID.get();
    }

    /**
     * @return Returns the counterReceive_EVT_ALL_SESSION_DATA.
     */
    public long getCounterReceive_EVT_ALL_SESSION_DATA() {
        return counterReceive_EVT_ALL_SESSION_DATA.get();
    }

    /**
     * @return Returns the counterReceive_EVT_GET_ALL_SESSIONS.
     */
    public long getCounterReceive_EVT_GET_ALL_SESSIONS() {
        return counterReceive_EVT_GET_ALL_SESSIONS.get();
    }

    /**
     * @return Returns the counterReceive_EVT_SESSION_ACCESSED.
     */
    public long getCounterReceive_EVT_SESSION_ACCESSED() {
        return counterReceive_EVT_SESSION_ACCESSED.get();
    }

    /**
     * @return Returns the counterReceive_EVT_SESSION_CREATED.
     */
    public long getCounterReceive_EVT_SESSION_CREATED() {
        return counterReceive_EVT_SESSION_CREATED.get();
    }

    /**
     * @return Returns the counterReceive_EVT_SESSION_DELTA.
     */
    public long getCounterReceive_EVT_SESSION_DELTA() {
        return counterReceive_EVT_SESSION_DELTA.get();
    }

    /**
     * @return Returns the counterReceive_EVT_SESSION_EXPIRED.
     */
    public long getCounterReceive_EVT_SESSION_EXPIRED() {
        return counterReceive_EVT_SESSION_EXPIRED.get();
    }


    /**
     * @return Returns the counterReceive_EVT_ALL_SESSION_TRANSFERCOMPLETE.
     */
    public int getCounterReceive_EVT_ALL_SESSION_TRANSFERCOMPLETE() {
        return counterReceive_EVT_ALL_SESSION_TRANSFERCOMPLETE.get();
    }

    /**
     * @return Returns the counterReceive_EVT_CHANGE_SESSION_ID.
     */
    public long getCounterReceive_EVT_CHANGE_SESSION_ID() {
        return counterReceive_EVT_CHANGE_SESSION_ID.get();
    }

    /**
     * @return Returns the counterReceive_EVT_ALL_SESSION_NOCONTEXTMANAGER.
     */
    public long getCounterReceive_EVT_ALL_SESSION_NOCONTEXTMANAGER() {
        return counterReceive_EVT_ALL_SESSION_NOCONTEXTMANAGER.get();
    }

    /**
     * @return Returns the sessionReplaceCounter.
     */
    public long getSessionReplaceCounter() {
        return sessionReplaceCounter.get();
    }

    /**
     * @return Returns the counterNoStateTransferred.
     */
    public int getCounterNoStateTransferred() {
        return counterNoStateTransferred.get();
    }

    public int getReceivedQueueSize() {
        synchronized (receivedMessageQueue) {
            return receivedMessageQueue.size();
        }
    }

    /**
     * @return Returns the stateTransferTimeout.
     */
    public int getStateTransferTimeout() {
        return stateTransferTimeout;
    }

    /**
     * @param timeoutAllSession The timeout
     */
    public void setStateTransferTimeout(int timeoutAllSession) {
        this.stateTransferTimeout = timeoutAllSession;
    }

    /**
     * @return <code>true</code> if the state transfer is complete.
     */
    public boolean getStateTransferred() {
        return stateTransferred;
    }

    /**
     * Set that state transferred is complete
     *
     * @param stateTransferred Flag value
     */
    public void setStateTransferred(boolean stateTransferred) {
        this.stateTransferred = stateTransferred;
    }

    public boolean isNoContextManagerReceived() {
        return noContextManagerReceived;
    }

    public void setNoContextManagerReceived(boolean noContextManagerReceived) {
        this.noContextManagerReceived = noContextManagerReceived;
    }

    /**
     * @return the sendAllSessionsWaitTime in msec
     */
    public int getSendAllSessionsWaitTime() {
        return sendAllSessionsWaitTime;
    }

    /**
     * @param sendAllSessionsWaitTime The sendAllSessionsWaitTime to set at msec.
     */
    public void setSendAllSessionsWaitTime(int sendAllSessionsWaitTime) {
        this.sendAllSessionsWaitTime = sendAllSessionsWaitTime;
    }

    /**
     * @return the stateTimestampDrop.
     */
    public boolean isStateTimestampDrop() {
        return stateTimestampDrop;
    }

    /**
     * @param isTimestampDrop The new flag value
     */
    public void setStateTimestampDrop(boolean isTimestampDrop) {
        this.stateTimestampDrop = isTimestampDrop;
    }

    /**
     * @return the sendAllSessions.
     */
    public boolean isSendAllSessions() {
        return sendAllSessions;
    }

    /**
     * @param sendAllSessions The sendAllSessions to set.
     */
    public void setSendAllSessions(boolean sendAllSessions) {
        this.sendAllSessions = sendAllSessions;
    }

    /**
     * @return the sendAllSessionsSize.
     */
    public int getSendAllSessionsSize() {
        return sendAllSessionsSize;
    }

    /**
     * @param sendAllSessionsSize The sendAllSessionsSize to set.
     */
    public void setSendAllSessionsSize(int sendAllSessionsSize) {
        this.sendAllSessionsSize = sendAllSessionsSize;
    }

    /**
     * @return the notifySessionListenersOnReplication.
     */
    public boolean isNotifySessionListenersOnReplication() {
        return notifySessionListenersOnReplication;
    }

    /**
     * @param notifyListenersCreateSessionOnReplication The notifySessionListenersOnReplication to set.
     */
    public void setNotifySessionListenersOnReplication(boolean notifyListenersCreateSessionOnReplication) {
        this.notifySessionListenersOnReplication = notifyListenersCreateSessionOnReplication;
    }


    public boolean isExpireSessionsOnShutdown() {
        return expireSessionsOnShutdown;
    }

    public void setExpireSessionsOnShutdown(boolean expireSessionsOnShutdown) {
        this.expireSessionsOnShutdown = expireSessionsOnShutdown;
    }

    public boolean isNotifyContainerListenersOnReplication() {
        return notifyContainerListenersOnReplication;
    }

    public void setNotifyContainerListenersOnReplication(boolean notifyContainerListenersOnReplication) {
        this.notifyContainerListenersOnReplication = notifyContainerListenersOnReplication;
    }

    /**
     * @return the enableStatistics
     */
    public boolean getEnableStatistics() {
        return this.enableStatistics;
    }

    /**
     * @param enableStatistics the enableStatistics to set
     */
    public void setEnableStatistics(boolean enableStatistics) {
        this.enableStatistics = enableStatistics;
    }

    // --------------------------------------------------------- Public Methods

    @Override
    public Session createSession(String sessionId) {
        return createSession(sessionId, true);
    }

    /**
     * Create new session with check maxActiveSessions and send session creation to other cluster nodes.
     *
     * @param sessionId  The session id that should be used for the session
     * @param distribute <code>true</code> to replicate the new session
     *
     * @return The session
     */
    public Session createSession(String sessionId, boolean distribute) {
        DeltaSession session = (DeltaSession) super.createSession(sessionId);
        if (distribute) {
            sendCreateSession(session.getId(), session);
        }
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("deltaManager.createSession.newSession", session.getId(),
                    Integer.valueOf(sessions.size())));
        }
        return session;
    }

    /**
     * Send create session event to all backup node
     *
     * @param sessionId The session id of the session
     * @param session   The session object
     */
    protected void sendCreateSession(String sessionId, DeltaSession session) {
        if (cluster.getMembers().length > 0) {
            SessionMessage msg = new SessionMessageImpl(getName(), SessionMessage.EVT_SESSION_CREATED, null, sessionId,
                    sessionId + "-" + System.currentTimeMillis());
            if (log.isTraceEnabled()) {
                log.trace(sm.getString("deltaManager.sendMessage.newSession", name, sessionId));
            }
            msg.setTimestamp(session.getCreationTime());
            if (enableStatistics) {
                counterSend_EVT_SESSION_CREATED.incrementAndGet();
            }
            send(msg);
        }
    }

    /**
     * Send messages to other backup member (domain or all)
     *
     * @param msg Session message
     */
    protected void send(SessionMessage msg) {
        if (cluster != null) {
            cluster.send(msg);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Creates new DeltaSession instance.
     */
    @Override
    public Session createEmptySession() {
        return new DeltaSession(this);
    }

    @Override
    public String rotateSessionId(Session session) {
        return rotateSessionId(session, true);
    }

    @Override
    public void changeSessionId(Session session, String newId) {
        changeSessionId(session, newId, true);
    }

    protected String rotateSessionId(Session session, boolean notify) {
        String orgSessionID = session.getId();
        String newId = super.rotateSessionId(session);
        if (notify) {
            sendChangeSessionId(session.getId(), orgSessionID);
        }
        return newId;
    }

    protected void changeSessionId(Session session, String newId, boolean notify) {
        String orgSessionID = session.getId();
        super.changeSessionId(session, newId);
        if (notify) {
            sendChangeSessionId(session.getId(), orgSessionID);
        }
    }

    protected void sendChangeSessionId(String newSessionID, String orgSessionID) {
        if (cluster.getMembers().length > 0) {
            try {
                // serialize sessionID
                byte[] data = serializeSessionId(newSessionID);
                // notify change sessionID
                SessionMessage msg = new SessionMessageImpl(getName(), SessionMessage.EVT_CHANGE_SESSION_ID, data,
                        orgSessionID, orgSessionID + "-" + System.currentTimeMillis());
                msg.setTimestamp(System.currentTimeMillis());
                if (enableStatistics) {
                    counterSend_EVT_CHANGE_SESSION_ID.incrementAndGet();
                }
                send(msg);
            } catch (IOException e) {
                log.error(sm.getString("deltaManager.unableSerializeSessionID", newSessionID), e);
            }
        }
    }

    /**
     * serialize sessionID
     *
     * @param sessionId Session id to serialize
     *
     * @return byte array with serialized session id
     *
     * @throws IOException if an input/output error occurs
     */
    protected byte[] serializeSessionId(String sessionId) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeUTF(sessionId);
        oos.flush();
        oos.close();
        return bos.toByteArray();
    }

    /**
     * Load sessionID
     *
     * @param data serialized session id
     *
     * @return session id
     *
     * @throws IOException if an input/output error occurs
     */
    protected String deserializeSessionId(byte[] data) throws IOException {
        ReplicationStream ois = getReplicationStream(data);
        String sessionId = ois.readUTF();
        ois.close();
        return sessionId;
    }

    /**
     * Load sessions from other cluster node.
     * <p>
     * FIXME replace currently sessions with same id without notification.
     * <p>
     * FIXME SSO handling is not really correct with the session replacement!
     *
     * @param data Serialized data
     *
     * @exception ClassNotFoundException if a serialized class cannot be found during the reload
     * @exception IOException            if an input/output error occurs
     */
    protected void deserializeSessions(byte[] data) throws ClassNotFoundException, IOException {

        // Open an input stream to the specified pathname, if any
        // Load the previously unloaded active sessions
        try (ObjectInputStream ois = getReplicationStream(data)) {
            Integer count = (Integer) ois.readObject();
            int n = count.intValue();
            for (int i = 0; i < n; i++) {
                DeltaSession session = (DeltaSession) createEmptySession();
                session.readObjectData(ois);
                session.setManager(this);
                session.setValid(true);
                session.setPrimarySession(false);
                // in case the nodes in the cluster are out of
                // time synch, this will make sure that we have the
                // correct timestamp, isValid returns true, cause
                // accessCount=1
                session.access();
                // make sure that the session gets ready to expire if
                // needed
                session.setAccessCount(0);
                session.resetDeltaRequest();
                // FIXME How inform other session id cache like SingleSignOn
                if (findSession(session.getIdInternal()) != null) {
                    if (enableStatistics) {
                        sessionReplaceCounter.incrementAndGet();
                    }
                    // FIXME better is to grap this sessions again !
                    if (log.isWarnEnabled()) {
                        log.warn(sm.getString("deltaManager.loading.existing.session", session.getIdInternal()));
                    }
                }
                add(session);
                if (notifySessionListenersOnReplication) {
                    session.tellNew();
                }
            }
        } catch (ClassNotFoundException e) {
            log.error(sm.getString("deltaManager.loading.cnfe", e), e);
            throw e;
        } catch (IOException e) {
            log.error(sm.getString("deltaManager.loading.ioe", e), e);
            throw e;
        }
    }


    /**
     * Save any currently active sessions in the appropriate persistence mechanism, if any. If persistence is not
     * supported, this method returns without doing anything.
     *
     * @param currentSessions Sessions to serialize
     *
     * @return serialized data
     *
     * @exception IOException if an input/output error occurs
     */
    protected byte[] serializeSessions(Session[] currentSessions) throws IOException {

        // Open an output stream to the specified pathname, if any
        ByteArrayOutputStream fos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(fos))) {
            oos.writeObject(Integer.valueOf(currentSessions.length));
            for (Session currentSession : currentSessions) {
                ((DeltaSession) currentSession).writeObjectData(oos);
            }
            // Flush and close the output stream
            oos.flush();
        } catch (IOException e) {
            log.error(sm.getString("deltaManager.unloading.ioe", e), e);
            throw e;
        }

        // send object data as byte[]
        return fos.toByteArray();
    }

    /**
     * Start this component and implement the requirements of
     * {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error that prevents this component from being
     *                                   used
     */
    @Override
    protected void startInternal() throws LifecycleException {

        super.startInternal();

        // Load unloaded sessions, if any
        try {
            if (cluster == null) {
                log.error(sm.getString("deltaManager.noCluster", getName()));
                return;
            } else {
                if (log.isInfoEnabled()) {
                    String type = "unknown";
                    if (cluster.getContainer() instanceof Host) {
                        type = "Host";
                    } else if (cluster.getContainer() instanceof Engine) {
                        type = "Engine";
                    }
                    log.info(sm.getString("deltaManager.registerCluster", getName(), type, cluster.getClusterName()));
                }
            }
            if (log.isInfoEnabled()) {
                log.info(sm.getString("deltaManager.startClustering", getName()));
            }

            getAllClusterSessions();

        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(sm.getString("deltaManager.managerLoad"), t);
        }

        setState(LifecycleState.STARTING);
    }

    /**
     * get from first session master the backup from all clustered sessions
     *
     * @see #findSessionMasterMember()
     */
    public synchronized void getAllClusterSessions() {
        if (cluster != null && cluster.getMembers().length > 0) {
            long beforeSendTime = System.currentTimeMillis();
            Member mbr = findSessionMasterMember();
            if (mbr == null) { // No domain member found
                return;
            }
            SessionMessage msg = new SessionMessageImpl(this.getName(), SessionMessage.EVT_GET_ALL_SESSIONS, null,
                    "GET-ALL", "GET-ALL-" + getName());
            msg.setTimestamp(beforeSendTime);
            // set reference time
            stateTransferCreateSendTime = beforeSendTime;
            // request session state
            if (enableStatistics) {
                counterSend_EVT_GET_ALL_SESSIONS.incrementAndGet();
            }
            stateTransferred = false;
            // FIXME This send call block the deploy thread, when sender waitForAck is enabled
            try {
                synchronized (receivedMessageQueue) {
                    receiverQueue = true;
                }
                cluster.send(msg, mbr, Channel.SEND_OPTIONS_ASYNCHRONOUS);
                if (log.isInfoEnabled()) {
                    log.info(sm.getString("deltaManager.waitForSessionState", getName(), mbr,
                            Integer.valueOf(getStateTransferTimeout())));
                }
                // FIXME At sender ack mode this method check only the state
                // transfer and resend is a problem!
                waitForSendAllSessions(beforeSendTime);
            } finally {
                synchronized (receivedMessageQueue) {
                    for (SessionMessage smsg : receivedMessageQueue) {
                        if (!stateTimestampDrop) {
                            messageReceived(smsg, smsg.getAddress());
                        } else {
                            if (smsg.getEventType() != SessionMessage.EVT_GET_ALL_SESSIONS &&
                                    smsg.getTimestamp() >= stateTransferCreateSendTime) {
                                // FIXME handle EVT_GET_ALL_SESSIONS later
                                messageReceived(smsg, smsg.getAddress());
                            } else {
                                if (log.isWarnEnabled()) {
                                    log.warn(sm.getString("deltaManager.dropMessage", getName(),
                                            smsg.getEventTypeString(), new Date(stateTransferCreateSendTime),
                                            new Date(smsg.getTimestamp())));
                                }
                            }
                        }
                    }
                    receivedMessageQueue.clear();
                    receiverQueue = false;
                }
            }
        } else {
            if (log.isInfoEnabled()) {
                log.info(sm.getString("deltaManager.noMembers", getName()));
            }
        }
    }

    /**
     * Find the master of the session state
     *
     * @return master member of sessions
     */
    protected Member findSessionMasterMember() {
        Member mbr = null;
        Member[] mbrs = cluster.getMembers();
        if (mbrs.length != 0) {
            mbr = mbrs[0];
        }
        if (mbr == null && log.isWarnEnabled()) {
            log.warn(sm.getString("deltaManager.noMasterMember", getName(), ""));
        }
        if (mbr != null && log.isTraceEnabled()) {
            log.trace(sm.getString("deltaManager.foundMasterMember", getName(), mbr));
        }
        return mbr;
    }

    /**
     * Wait that cluster session state is transferred or timeout after 60 Sec With stateTransferTimeout == -1 wait that
     * backup is transferred (forever mode)
     *
     * @param beforeSendTime Start instant of the operation
     */
    protected void waitForSendAllSessions(long beforeSendTime) {
        long reqStart = System.currentTimeMillis();
        long reqNow = reqStart;
        boolean isTimeout = false;
        if (getStateTransferTimeout() > 0) {
            // wait that state is transferred with timeout check
            do {
                try {
                    Thread.sleep(100);
                } catch (Exception sleep) {
                    //
                }
                reqNow = System.currentTimeMillis();
                isTimeout = ((reqNow - reqStart) > (1000L * getStateTransferTimeout()));
            } while ((!getStateTransferred()) && (!isTimeout) && (!isNoContextManagerReceived()));
        } else {
            if (getStateTransferTimeout() == -1) {
                // wait that state is transferred
                do {
                    try {
                        Thread.sleep(100);
                    } catch (Exception sleep) {
                        // Ignore
                    }
                } while ((!getStateTransferred()) && (!isNoContextManagerReceived()));
                reqNow = System.currentTimeMillis();
            }
        }
        if (isTimeout) {
            if (enableStatistics) {
                counterNoStateTransferred.incrementAndGet();
            }
            log.error(sm.getString("deltaManager.noSessionState", getName(), new Date(beforeSendTime),
                    Long.valueOf(reqNow - beforeSendTime)));
        } else if (isNoContextManagerReceived()) {
            if (log.isWarnEnabled()) {
                log.warn(sm.getString("deltaManager.noContextManager", getName(), new Date(beforeSendTime),
                        Long.valueOf(reqNow - beforeSendTime)));
            }
        } else {
            if (log.isInfoEnabled()) {
                log.info(sm.getString("deltaManager.sessionReceived", getName(), new Date(beforeSendTime),
                        Long.valueOf(reqNow - beforeSendTime)));
            }
        }
    }

    /**
     * Stop this component and implement the requirements of
     * {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error that prevents this component from being
     *                                   used
     */
    @Override
    protected void stopInternal() throws LifecycleException {

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("deltaManager.stopped", getName()));
        }

        setState(LifecycleState.STOPPING);

        // Expire all active sessions
        if (log.isInfoEnabled()) {
            log.info(sm.getString("deltaManager.expireSessions", getName()));
        }
        Session[] sessions = findSessions();
        for (Session value : sessions) {
            DeltaSession session = (DeltaSession) value;
            if (!session.isValid()) {
                continue;
            }
            try {
                session.expire(true, isExpireSessionsOnShutdown());
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
            }
        }

        // Require a new random number generator if we are restarted
        super.stopInternal();
    }

    // -------------------------------------------------------- Replication
    // Methods

    @Override
    public void messageDataReceived(ClusterMessage cmsg) {
        if (cmsg instanceof SessionMessage msg) {
            switch (msg.getEventType()) {
                case SessionMessage.EVT_GET_ALL_SESSIONS:
                case SessionMessage.EVT_SESSION_CREATED:
                case SessionMessage.EVT_SESSION_EXPIRED:
                case SessionMessage.EVT_SESSION_ACCESSED:
                case SessionMessage.EVT_SESSION_DELTA:
                case SessionMessage.EVT_CHANGE_SESSION_ID:
                    synchronized (receivedMessageQueue) {
                        if (receiverQueue) {
                            receivedMessageQueue.add(msg);
                            return;
                        }
                    }
                    break;
                default:
                    // we didn't queue, do nothing
                    break;
            } // switch

            messageReceived(msg, msg.getAddress());
        }
    }

    @Override
    public ClusterMessage requestCompleted(String sessionId) {
        return requestCompleted(sessionId, false);
    }

    /**
     * When the request has been completed, the replication valve will notify the manager, and the manager will decide
     * whether any replication is needed or not. If there is a need for replication, the manager will create a session
     * message and that will be replicated. The cluster determines where it gets sent. Session expiration also calls
     * this method, but with expires == true.
     *
     * @param sessionId - the sessionId that just completed.
     * @param expires   - whether this method has been called during session expiration
     *
     * @return a SessionMessage to be sent,
     */
    public ClusterMessage requestCompleted(String sessionId, boolean expires) {
        DeltaSession session;
        SessionMessage msg = null;
        try {
            session = (DeltaSession) findSession(sessionId);
            if (session == null) {
                // A parallel request has called session.invalidate() which has
                // removed the session from the Manager.
                return null;
            }
            if (session.isDirty()) {
                if (enableStatistics) {
                    counterSend_EVT_SESSION_DELTA.incrementAndGet();
                }
                msg = new SessionMessageImpl(getName(), SessionMessage.EVT_SESSION_DELTA, session.getDiff(), sessionId,
                        sessionId + "-" + System.currentTimeMillis());
            }
        } catch (IOException x) {
            log.error(sm.getString("deltaManager.createMessage.unableCreateDeltaRequest", sessionId), x);
            return null;
        }
        if (msg == null) {
            if (!expires && !session.isPrimarySession()) {
                if (enableStatistics) {
                    counterSend_EVT_SESSION_ACCESSED.incrementAndGet();
                }
                msg = new SessionMessageImpl(getName(), SessionMessage.EVT_SESSION_ACCESSED, null, sessionId,
                        sessionId + "-" + System.currentTimeMillis());
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("deltaManager.createMessage.accessChangePrimary", getName(), sessionId));
                }
            }
        } else { // log only outside synch block!
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("deltaManager.createMessage.delta", getName(), sessionId));
            }
        }
        if (!expires) {
            session.setPrimarySession(true);
        }
        // check to see if we need to send out an access message
        if (!expires && (msg == null)) {
            long replDelta = System.currentTimeMillis() - session.getLastTimeReplicated();
            if (session.getMaxInactiveInterval() >= 0 && replDelta > (session.getMaxInactiveInterval() * 1000L)) {
                if (enableStatistics) {
                    counterSend_EVT_SESSION_ACCESSED.incrementAndGet();
                }
                msg = new SessionMessageImpl(getName(), SessionMessage.EVT_SESSION_ACCESSED, null, sessionId,
                        sessionId + "-" + System.currentTimeMillis());
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("deltaManager.createMessage.access", getName(), sessionId));
                }
            }
        }

        // update last replicated time
        if (msg != null) {
            session.setLastTimeReplicated(System.currentTimeMillis());
            msg.setTimestamp(session.getLastTimeReplicated());
        }
        return msg;
    }

    /**
     * Reset manager statistics
     */
    public synchronized void resetStatistics() {
        processingTime = 0;
        expiredSessions.set(0);
        synchronized (sessionCreationTiming) {
            sessionCreationTiming.clear();
            while (sessionCreationTiming.size() < TIMING_STATS_CACHE_SIZE) {
                sessionCreationTiming.add(null);
            }
        }
        synchronized (sessionExpirationTiming) {
            sessionExpirationTiming.clear();
            while (sessionExpirationTiming.size() < TIMING_STATS_CACHE_SIZE) {
                sessionExpirationTiming.add(null);
            }
        }
        rejectedSessions = 0;
        sessionReplaceCounter.set(0);
        counterNoStateTransferred.set(0);
        setMaxActive(getActiveSessions());
        counterReceive_EVT_ALL_SESSION_DATA.set(0);
        counterReceive_EVT_GET_ALL_SESSIONS.set(0);
        counterReceive_EVT_SESSION_ACCESSED.set(0);
        counterReceive_EVT_SESSION_CREATED.set(0);
        counterReceive_EVT_SESSION_DELTA.set(0);
        counterReceive_EVT_SESSION_EXPIRED.set(0);
        counterReceive_EVT_ALL_SESSION_TRANSFERCOMPLETE.set(0);
        counterReceive_EVT_CHANGE_SESSION_ID.set(0);
        counterSend_EVT_ALL_SESSION_DATA.set(0);
        counterSend_EVT_GET_ALL_SESSIONS.set(0);
        counterSend_EVT_SESSION_ACCESSED.set(0);
        counterSend_EVT_SESSION_CREATED.set(0);
        counterSend_EVT_SESSION_DELTA.set(0);
        counterSend_EVT_SESSION_EXPIRED.set(0);
        counterSend_EVT_ALL_SESSION_TRANSFERCOMPLETE.set(0);
        counterSend_EVT_CHANGE_SESSION_ID.set(0);

    }

    // -------------------------------------------------------- expire

    /**
     * send session expired to other cluster nodes
     *
     * @param id session id
     */
    protected void sessionExpired(String id) {
        if (cluster.getMembers().length > 0) {
            if (enableStatistics) {
                counterSend_EVT_SESSION_EXPIRED.incrementAndGet();
            }
            SessionMessage msg = new SessionMessageImpl(getName(), SessionMessage.EVT_SESSION_EXPIRED, null, id,
                    id + "-EXPIRED-MSG");
            msg.setTimestamp(System.currentTimeMillis());
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("deltaManager.createMessage.expire", getName(), id));
            }
            send(msg);
        }
    }

    /**
     * Expire all find sessions.
     */
    public void expireAllLocalSessions() {
        Session[] sessions = findSessions();
        int expireDirect = 0;
        int expireIndirect = 0;

        long timeNow = 0;
        if (log.isTraceEnabled()) {
            timeNow = System.currentTimeMillis();
            log.trace("Start expire all sessions " + getName() + " at " + timeNow + " sessioncount " + sessions.length);
        }
        for (Session value : sessions) {
            if (value instanceof DeltaSession session) {
                if (session.isPrimarySession()) {
                    if (session.isValid()) {
                        session.expire();
                        expireDirect++;
                    } else {
                        expireIndirect++;
                    } // end if
                } // end if
            } // end if
        } // for
        if (log.isTraceEnabled()) {
            long timeEnd = System.currentTimeMillis();
            log.trace("End expire sessions " + getName() + " expire processingTime " + (timeEnd - timeNow) +
                    " expired direct sessions: " + expireDirect + " expired direct sessions: " + expireIndirect);
        }
    }

    @Override
    public String[] getInvalidatedSessions() {
        return EMPTY_STRING_ARRAY;
    }

    // -------------------------------------------------------- message receive

    /**
     * This method is called by the received thread when a SessionMessage has been received from one of the other nodes
     * in the cluster.
     *
     * @param msg    - the message received
     * @param sender - the sender of the message, this is used if we receive a EVT_GET_ALL_SESSION message, so that we
     *                   only reply to the requesting node
     */
    protected void messageReceived(SessionMessage msg, Member sender) {
        Thread currentThread = Thread.currentThread();
        ClassLoader contextLoader = currentThread.getContextClassLoader();
        try {

            ClassLoader[] loaders = getClassLoaders();
            currentThread.setContextClassLoader(loaders[0]);
            if (log.isTraceEnabled()) {
                log.trace(sm.getString("deltaManager.receiveMessage.eventType", getName(), msg.getEventTypeString(),
                        sender));
            }

            switch (msg.getEventType()) {
                case SessionMessage.EVT_GET_ALL_SESSIONS:
                    handleGET_ALL_SESSIONS(msg, sender);
                    break;
                case SessionMessage.EVT_ALL_SESSION_DATA:
                    handleALL_SESSION_DATA(msg, sender);
                    break;
                case SessionMessage.EVT_ALL_SESSION_TRANSFERCOMPLETE:
                    handleALL_SESSION_TRANSFERCOMPLETE(msg, sender);
                    break;
                case SessionMessage.EVT_SESSION_CREATED:
                    handleSESSION_CREATED(msg, sender);
                    break;
                case SessionMessage.EVT_SESSION_EXPIRED:
                    handleSESSION_EXPIRED(msg, sender);
                    break;
                case SessionMessage.EVT_SESSION_ACCESSED:
                    handleSESSION_ACCESSED(msg, sender);
                    break;
                case SessionMessage.EVT_SESSION_DELTA:
                    handleSESSION_DELTA(msg, sender);
                    break;
                case SessionMessage.EVT_CHANGE_SESSION_ID:
                    handleCHANGE_SESSION_ID(msg, sender);
                    break;
                case SessionMessage.EVT_ALL_SESSION_NOCONTEXTMANAGER:
                    handleALL_SESSION_NOCONTEXTMANAGER(msg, sender);
                    break;
                default:
                    // we didn't recognize the message type, do nothing
                    break;
            } // switch
        } catch (Exception x) {
            log.error(sm.getString("deltaManager.receiveMessage.error", getName()), x);
        } finally {
            currentThread.setContextClassLoader(contextLoader);
        }
    }

    // -------------------------------------------------------- message receiver handler


    /**
     * handle receive session state is complete transferred
     *
     * @param msg    Session message
     * @param sender Member which sent the message
     */
    protected void handleALL_SESSION_TRANSFERCOMPLETE(SessionMessage msg, Member sender) {
        if (enableStatistics) {
            counterReceive_EVT_ALL_SESSION_TRANSFERCOMPLETE.incrementAndGet();
        }
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("deltaManager.receiveMessage.transfercomplete", getName(), sender.getHost(),
                    Integer.valueOf(sender.getPort())));
        }
        stateTransferCreateSendTime = msg.getTimestamp();
        stateTransferred = true;
    }

    /**
     * handle receive session delta
     *
     * @param msg    Session message
     * @param sender Member which sent the message
     *
     * @throws IOException            IO error with serialization
     * @throws ClassNotFoundException Serialization error
     */
    protected void handleSESSION_DELTA(SessionMessage msg, Member sender) throws IOException, ClassNotFoundException {
        if (enableStatistics) {
            counterReceive_EVT_SESSION_DELTA.incrementAndGet();
        }
        byte[] delta = msg.getSession();
        DeltaSession session = (DeltaSession) findSession(msg.getSessionID());
        if (session == null) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("deltaManager.receiveMessage.delta.unknown", getName(), msg.getSessionID()));
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("deltaManager.receiveMessage.delta", getName(), msg.getSessionID()));
            }

            session.deserializeAndExecuteDeltaRequest(delta);
        }
    }

    /**
     * handle receive session is access at other node ( primary session is now false)
     *
     * @param msg    Session message
     * @param sender Member which sent the message
     *
     * @throws IOException Propagated IO error
     */
    protected void handleSESSION_ACCESSED(SessionMessage msg, Member sender) throws IOException {
        if (enableStatistics) {
            counterReceive_EVT_SESSION_ACCESSED.incrementAndGet();
        }
        DeltaSession session = (DeltaSession) findSession(msg.getSessionID());
        if (session != null) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("deltaManager.receiveMessage.accessed", getName(), msg.getSessionID()));
            }
            session.access();
            session.setPrimarySession(false);
            session.endAccess();
        }
    }

    /**
     * handle receive session is expired at other node ( expire session also here)
     *
     * @param msg    Session message
     * @param sender Member which sent the message
     *
     * @throws IOException Propagated IO error
     */
    protected void handleSESSION_EXPIRED(SessionMessage msg, Member sender) throws IOException {
        if (enableStatistics) {
            counterReceive_EVT_SESSION_EXPIRED.incrementAndGet();
        }
        DeltaSession session = (DeltaSession) findSession(msg.getSessionID());
        if (session != null) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("deltaManager.receiveMessage.expired", getName(), msg.getSessionID()));
            }
            session.expire(notifySessionListenersOnReplication, false);
        }
    }

    /**
     * handle receive new session is created at other node (create backup - primary false)
     *
     * @param msg    Session message
     * @param sender Member which sent the message
     */
    protected void handleSESSION_CREATED(SessionMessage msg, Member sender) {
        if (enableStatistics) {
            counterReceive_EVT_SESSION_CREATED.incrementAndGet();
        }
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("deltaManager.receiveMessage.createNewSession", getName(), msg.getSessionID()));
        }
        DeltaSession session = (DeltaSession) createEmptySession();
        session.setValid(true);
        session.setPrimarySession(false);
        session.setCreationTime(msg.getTimestamp());
        // use container maxInactiveInterval so that session will expire correctly
        // in case of primary transfer
        session.setMaxInactiveInterval(getContext().getSessionTimeout() * 60, false);
        session.access();
        session.setId(msg.getSessionID(), notifySessionListenersOnReplication);
        session.endAccess();

    }

    /**
     * handle receive sessions from other not ( restart )
     *
     * @param msg    Session message
     * @param sender Member which sent the message
     *
     * @throws ClassNotFoundException Serialization error
     * @throws IOException            IO error with serialization
     */
    protected void handleALL_SESSION_DATA(SessionMessage msg, Member sender)
            throws ClassNotFoundException, IOException {
        if (enableStatistics) {
            counterReceive_EVT_ALL_SESSION_DATA.incrementAndGet();
        }
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("deltaManager.receiveMessage.allSessionDataBegin", getName()));
        }
        byte[] data = msg.getSession();
        deserializeSessions(data);
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("deltaManager.receiveMessage.allSessionDataAfter", getName()));
        }
        // stateTransferred = true;
    }

    /**
     * Handle a get all sessions message from another node. Depending on {@link #sendAllSessions}, sessions are either
     * sent in a single message or in batches. Sending is complete when this method exits.
     *
     * @param msg    Session message
     * @param sender Member which sent the message
     *
     * @throws IOException IO error sending messages
     */
    protected void handleGET_ALL_SESSIONS(SessionMessage msg, Member sender) throws IOException {
        if (enableStatistics) {
            counterReceive_EVT_GET_ALL_SESSIONS.incrementAndGet();
        }
        // get a list of all the session from this manager
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("deltaManager.receiveMessage.unloadingBegin", getName()));
        }
        // Write the number of active sessions, followed by the details
        // get all sessions and serialize without sync
        Session[] currentSessions = findSessions();
        long findSessionTimestamp = System.currentTimeMillis();
        if (isSendAllSessions()) {
            sendSessions(sender, currentSessions, findSessionTimestamp);
        } else {
            // send sessions in batches
            int remain = currentSessions.length;
            for (int i = 0; i < currentSessions.length; i += getSendAllSessionsSize()) {
                int len = i + getSendAllSessionsSize() > currentSessions.length ? currentSessions.length - i :
                        getSendAllSessionsSize();
                Session[] sendSessions = new Session[len];
                System.arraycopy(currentSessions, i, sendSessions, 0, len);
                sendSessions(sender, sendSessions, findSessionTimestamp);
                remain = remain - len;
                if (getSendAllSessionsWaitTime() > 0 && remain > 0) {
                    try {
                        Thread.sleep(getSendAllSessionsWaitTime());
                    } catch (Exception sleep) {
                        // Ignore
                    }
                }
            }
        }

        SessionMessage newmsg = new SessionMessageImpl(name, SessionMessage.EVT_ALL_SESSION_TRANSFERCOMPLETE, null,
                "SESSION-STATE-TRANSFERRED", "SESSION-STATE-TRANSFERRED" + getName());
        newmsg.setTimestamp(findSessionTimestamp);
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("deltaManager.createMessage.allSessionTransferred", getName()));
        }
        if (enableStatistics) {
            counterSend_EVT_ALL_SESSION_TRANSFERCOMPLETE.incrementAndGet();
        }
        cluster.send(newmsg, sender);
    }

    /**
     * handle receive change sessionID at other node
     *
     * @param msg    Session message
     * @param sender Member which sent the message
     *
     * @throws IOException IO error with serialization
     */
    protected void handleCHANGE_SESSION_ID(SessionMessage msg, Member sender) throws IOException {
        if (enableStatistics) {
            counterReceive_EVT_CHANGE_SESSION_ID.incrementAndGet();
        }
        DeltaSession session = (DeltaSession) findSession(msg.getSessionID());
        if (session != null) {
            String newSessionID = deserializeSessionId(msg.getSession());
            session.setPrimarySession(false);
            // change session id
            changeSessionId(session, newSessionID, notifySessionListenersOnReplication,
                    notifyContainerListenersOnReplication);
        }
    }

    /**
     * handle receive no context manager.
     *
     * @param msg    Session message
     * @param sender Member which sent the message
     */
    protected void handleALL_SESSION_NOCONTEXTMANAGER(SessionMessage msg, Member sender) {
        if (enableStatistics) {
            counterReceive_EVT_ALL_SESSION_NOCONTEXTMANAGER.incrementAndGet();
        }
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("deltaManager.receiveMessage.noContextManager", getName(), sender.getHost(),
                    Integer.valueOf(sender.getPort())));
        }
        noContextManagerReceived = true;
    }

    /**
     * send a block of session to sender
     *
     * @param sender          Sender member
     * @param currentSessions Sessions to send
     * @param sendTimestamp   Timestamp
     *
     * @throws IOException IO error sending messages
     */
    protected void sendSessions(Member sender, Session[] currentSessions, long sendTimestamp) throws IOException {
        byte[] data = serializeSessions(currentSessions);
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("deltaManager.receiveMessage.unloadingAfter", getName()));
        }
        SessionMessage newmsg = new SessionMessageImpl(name, SessionMessage.EVT_ALL_SESSION_DATA, data, "SESSION-STATE",
                "SESSION-STATE-" + getName());
        newmsg.setTimestamp(sendTimestamp);
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("deltaManager.createMessage.allSessionData", getName()));
        }
        if (enableStatistics) {
            counterSend_EVT_ALL_SESSION_DATA.incrementAndGet();
        }
        int sendOptions = Channel.SEND_OPTIONS_SYNCHRONIZED_ACK | Channel.SEND_OPTIONS_USE_ACK;
        cluster.send(newmsg, sender, sendOptions);
    }

    @Override
    public ClusterManager cloneFromTemplate() {
        DeltaManager result = new DeltaManager();
        clone(result);
        result.expireSessionsOnShutdown = expireSessionsOnShutdown;
        result.notifySessionListenersOnReplication = notifySessionListenersOnReplication;
        result.notifyContainerListenersOnReplication = notifyContainerListenersOnReplication;
        result.stateTransferTimeout = stateTransferTimeout;
        result.sendAllSessions = sendAllSessions;
        result.sendAllSessionsSize = sendAllSessionsSize;
        result.sendAllSessionsWaitTime = sendAllSessionsWaitTime;
        result.stateTimestampDrop = stateTimestampDrop;
        return result;
    }
}
