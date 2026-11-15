package dev.rakrecorder;
import org.junit.Test;
import java.io.*;
import java.nio.*;
import static org.junit.Assert.*;
public class PcapReaderTest {
    private byte[] capture(ByteOrder order) throws Exception {
        ByteBuffer b=ByteBuffer.allocate(24+16+28).order(order); b.putInt(0xa1b2c3d4).putShort((short)2).putShort((short)4).putInt(0).putInt(0).putInt(65535).putInt(101);
        b.putInt(10).putInt(123456).putInt(28).putInt(28); byte[] p=new byte[28]; p[0]=0x45;p[9]=17;p[20]=0x14;p[21]=0x05;p[22]=0x12;p[23]=0x34;b.put(p);return b.array();
    }
    @Test public void parsesBothByteOrders() throws Exception { for(ByteOrder o:new ByteOrder[]{ByteOrder.BIG_ENDIAN,ByteOrder.LITTLE_ENDIAN}) {PcapReader.Result r=PcapReader.analyze(new ByteArrayInputStream(capture(o))); assertEquals(1,r.packets);assertEquals(1,r.udp);assertEquals(1,r.udp5125);assertEquals(10123456,r.firstMicros);} }
    @Test(expected=EOFException.class) public void rejectsTruncated() throws Exception {byte[] c=capture(ByteOrder.LITTLE_ENDIAN); PcapReader.analyze(new ByteArrayInputStream(java.util.Arrays.copyOf(c,c.length-1)));}
    @Test(expected=IOException.class) public void rejectsUnknownFormat() throws Exception {PcapReader.analyze(new ByteArrayInputStream(new byte[24]));}
}
