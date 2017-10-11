package de.bornemisza.loadbalancer.da;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.*;

import com.hazelcast.core.Cluster;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IList;
import com.hazelcast.core.IMap;
import com.hazelcast.core.OperationTimeoutException;
import de.bornemisza.loadbalancer.entity.PseudoHazelcastList;

import de.bornemisza.loadbalancer.entity.PseudoHazelcastMap;

public class PoolTest {

    private HashMap<String, Object> allConnections;
    private HazelcastInstance hazelcast;
    private IList hazelcastList;
    private IMap hazelcastMap;

    private final SecureRandom wheel = new SecureRandom();

    public PoolTest() {
    }

    @Before
    public void setUp() {
        this.allConnections = new HashMap<>();
        allConnections.put("host-1.domain.de", new Object());
        allConnections.put("host-2.domain.de", new Object());
        allConnections.put("host-3.domain.de", new Object());

        this.hazelcast = mock(HazelcastInstance.class);
        Cluster cluster = mock(Cluster.class);
        when(cluster.getMembers()).thenReturn(new HashSet<>());
        when(hazelcast.getCluster()).thenReturn(cluster);
        this.hazelcastList = new PseudoHazelcastList<>();
        this.hazelcastMap = mock(IMap.class);
    }

    @Test
    public void hazelcastWorking() {
        when(hazelcastMap.isEmpty()).thenReturn(true);
        when(hazelcast.getList(anyString())).thenReturn(hazelcastList);
        when(hazelcast.getMap(anyString())).thenReturn(hazelcastMap);
        Pool CUT = new PoolImpl(allConnections, hazelcast);
        List<String> dbServers = CUT.getDbServerQueue();
        assertEquals(hazelcastList, dbServers);
        assertEquals(CUT.getAllHostnames().size(), dbServers.size());
        Map<String, Object> dbServerUtilisation = CUT.getDbServerUtilisation();
        assertEquals(hazelcastMap, dbServerUtilisation);
        for (String hostname : allConnections.keySet()) {
            assertTrue(dbServers.contains(hostname));
            verify(dbServerUtilisation).isEmpty();
            verify(dbServerUtilisation).containsKey(hostname);
            verify(dbServerUtilisation).put(hostname, 0);
        }

        // subsequent invocations should just return the created data structures
        assertEquals(dbServers, CUT.getDbServerQueue());
        assertEquals(dbServerUtilisation, CUT.getDbServerUtilisation());
        verifyNoMoreInteractions(dbServerUtilisation);
    }

    @Test
    public void verifyRequestCounting() {
        when(hazelcast.getList(anyString())).thenReturn(hazelcastList);
        IMap utilisationMap = new PseudoHazelcastMap<>();
        when(hazelcast.getMap(anyString())).thenReturn(utilisationMap);
        Pool CUT = new PoolImpl(allConnections, hazelcast);
        List<String> allHostnames = Arrays.asList(new String[] { "host1", "host2", "host3.hosts.de" });
        int requestCount = wheel.nextInt(15) + 1;
        for (String hostname : allHostnames) {
            utilisationMap.put(hostname, 0);
            for (int i = 0; i < requestCount; i++) {
                CUT.trackUtilisation(hostname);
            }
        }
        for (String hostname : allHostnames) {
            assertEquals(requestCount, CUT.getDbServerUtilisation().get(hostname));
        }
        
    }

    @Test
    public void hazelcastNotWorkingAtFirst_thenWorkingLater() {
        String errMsg = "Invocation failed to complete due to operation-heartbeat-timeout";
        when(hazelcast.getList(anyString()))
                .thenThrow(new OperationTimeoutException(errMsg))
                .thenThrow(new OperationTimeoutException(errMsg))
                .thenReturn(hazelcastList);
        when(hazelcast.getMap(anyString()))
                .thenThrow(new OperationTimeoutException(errMsg))
                .thenThrow(new OperationTimeoutException(errMsg))
                .thenReturn(hazelcastMap);

        Pool CUT = new PoolImpl(allConnections, hazelcast);
        List<String> dbServers = CUT.getDbServerQueue();
        assertTrue(dbServers.isEmpty()); // We have not been successful in establishing a connection to Hazelcast upon initialisation
        dbServers = CUT.getDbServerQueue(); // third invocation was successful, because Hazelcast returned to service
        assertEquals(CUT.getAllHostnames().size(), dbServers.size());

        Map<String, Object> dbServerUtilisation = CUT.getDbServerUtilisation();
        assertNotEquals(hazelcastMap, dbServerUtilisation);

        // subsequent invocations should return the Hazelcast data structure
        assertEquals(hazelcastList.hashCode(), CUT.getDbServerQueue().hashCode());
        assertEquals(hazelcastMap.hashCode(), CUT.getDbServerUtilisation().hashCode());
    }

    public class PoolImpl extends Pool {

        public PoolImpl(Map<String, Object> allConnections, HazelcastInstance hazelcast) {
            super(allConnections, hazelcast);
        }
    }
    
}
