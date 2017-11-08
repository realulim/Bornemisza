package de.bornemisza.maintenance.task;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.ITopic;

import de.bornemisza.couchdb.entity.CouchDbConnection;
import de.bornemisza.loadbalancer.ClusterEvent;
import de.bornemisza.loadbalancer.ClusterEvent.ClusterEventType;
import de.bornemisza.maintenance.CouchAdminPool;
import de.bornemisza.maintenance.entity.PseudoHazelcastMap;

public class HealthCheckTaskTest {

    private String hostname;
    private ITopic clusterMaintenanceTopic;
    private ArgumentCaptor<ClusterEvent> captor;
    private HealthChecks healthChecks;
    private HealthCheckTask CUT;
    
    public HealthCheckTaskTest() {
    }
    
    @Before
    public void setUp() {
        HazelcastInstance hazelcast = mock(HazelcastInstance.class);
        clusterMaintenanceTopic = mock(ITopic.class);
        when(hazelcast.getReliableTopic(anyString())).thenReturn(clusterMaintenanceTopic);
        captor = ArgumentCaptor.forClass(ClusterEvent.class);

        hostname = "db-1.domain.de";
        IMap connections = new PseudoHazelcastMap();
        connections.put(hostname, mock(CouchDbConnection.class));
        
        CouchAdminPool couchPool = mock(CouchAdminPool.class);
        when(couchPool.getAllConnections()).thenReturn(connections);
        healthChecks = mock(HealthChecks.class);

        CUT = new HealthCheckTask(couchPool, clusterMaintenanceTopic, healthChecks);
    }

    @Test
    public void healthChecks() {
        when(healthChecks.isCouchDbReady(any(CouchDbConnection.class)))
                .thenReturn(true)
                .thenReturn(false)
                .thenReturn(false)
                .thenReturn(true);
        CUT.healthChecks();
        verify(clusterMaintenanceTopic, times(0)).publish(any());
        CUT.healthChecks();
        verify(clusterMaintenanceTopic, times(1)).publish(captor.capture());
        CUT.healthChecks();
        verify(clusterMaintenanceTopic, times(1)).publish(any());
        CUT.healthChecks();
        verify(clusterMaintenanceTopic, times(2)).publish(captor.capture());

        List<ClusterEvent> clusterEvents = captor.getAllValues();
        assertEquals(3, clusterEvents.size());
        assertEquals(hostname, clusterEvents.get(0).getHostname());
        assertEquals(ClusterEventType.HOST_UNHEALTHY, clusterEvents.get(0).getType());
        assertEquals(hostname, clusterEvents.get(2).getHostname());
        assertEquals(ClusterEventType.HOST_HEALTHY, clusterEvents.get(2).getType());
    }

}
