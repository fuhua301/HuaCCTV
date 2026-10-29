package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Map;

public class AppTrafficStatsTest {
    @Test public void sumsUidStatesAndInterfacesWithoutDeviceOrTagDuplicates() {
        String dump="Dev stats:\nident=x uid=10001 set=DEFAULT tag=0x0\nst=1 rb=999 tb=999\n"
            +"UID stats:\nident=[wifi] uid=10001 set=DEFAULT tag=0x0\nst=1 rb=100 tb=20\nst=2 rb=200 tb=30\n"
            +"ident=[eth] uid=10001 set=FOREGROUND tag=0x0\nst=3 rb=400 tb=50\n"
            +"ident=[eth] uid=10002 set=DEFAULT tag=0x0\nst=3 rb=10 tb=5\n"
            +"ident=[eth] uid=10001 set=DBG_VPN_IN tag=0x0\nst=3 rb=800 tb=800\n"
            +"UID tag stats:\nident=[eth] uid=10001 set=DEFAULT tag=0x1\nst=3 rb=10000 tb=10000\n";
        Map<Integer,long[]> totals=AppTrafficStats.parse(dump);
        assertArrayEquals(new long[]{700,100},totals.get(10001));assertArrayEquals(new long[]{10,5},totals.get(10002));assertEquals(2,totals.size());
    }
    @Test public void timestampAndResetHandling() {
        assertEquals(500,AppTrafficStats.rate(100,1600,3000));
        assertEquals(0,AppTrafficStats.rate(1000,5,2000));
        assertEquals(0,AppTrafficStats.rate(1,2,0));
    }
    @Test public void unavailableDataDoesNotPretendZeroSpeed() {
        try {AppTrafficStats.parse("Permission Denial");fail();}catch(IllegalArgumentException expected){}
        assertTrue(AppTrafficStats.parse("UID stats:\n  Pending bytes: 0\n").isEmpty());
    }
    @Test public void units() {
        assertEquals("0 B/s",AppTrafficStats.speed(0));assertEquals("1.5 KB/s",AppTrafficStats.speed(1536));assertEquals("2.00 MB/s",AppTrafficStats.speed(2097152));
    }
}
