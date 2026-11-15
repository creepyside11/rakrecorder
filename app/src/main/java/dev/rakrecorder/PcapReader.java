package dev.rakrecorder;

import java.io.*;
import java.nio.*;
import java.util.*;

/** Streaming classic PCAP reader. Rejects truncated or malformed records. */
public final class PcapReader {
    private static final int MAX_PACKET = 16 * 1024 * 1024;
    public static final class Result {
        public long packets, tcp, udp, udp5125, udp5127, other, firstMicros = -1, lastMicros = -1;
        public int linkType;
    }
    private static byte[] read(InputStream in, int n, boolean eofAllowed) throws IOException {
        byte[] b = new byte[n]; int p = 0;
        while (p < n) { int got = in.read(b, p, n-p); if (got < 0) { if (p == 0 && eofAllowed) return null; throw new EOFException("Обрезанный PCAP"); } if (got == 0) { int one = in.read(); if (one < 0) throw new EOFException("Обрезанный PCAP"); b[p++] = (byte)one; } else p += got; }
        return b;
    }
    private static long uint(int v) { return Integer.toUnsignedLong(v); }
    public static Result analyze(InputStream source) throws IOException {
        InputStream in = new BufferedInputStream(source); byte[] header = read(in,24,false);
        int magic = ByteBuffer.wrap(header).getInt(); boolean nano = false; ByteOrder order;
        switch (magic) {
            case 0xa1b2c3d4: order=ByteOrder.BIG_ENDIAN; break;
            case 0xd4c3b2a1: order=ByteOrder.LITTLE_ENDIAN; break;
            case 0xa1b23c4d: order=ByteOrder.BIG_ENDIAN; nano=true; break;
            case 0x4d3cb2a1: order=ByteOrder.LITTLE_ENDIAN; nano=true; break;
            default: throw new IOException("Поддерживается только классический PCAP (не PCAPNG)");
        }
        ByteBuffer h=ByteBuffer.wrap(header).order(order);
        if (Short.toUnsignedInt(h.getShort(4)) != 2 || Short.toUnsignedInt(h.getShort(6)) != 4) throw new IOException("Неподдерживаемая версия PCAP");
        long snap=uint(h.getInt(16)); if (snap == 0 || snap > MAX_PACKET) throw new IOException("Недопустимый snaplen");
        Result r=new Result(); r.linkType=h.getInt(20) & 0xffff;
        if (r.linkType != 101 && r.linkType != 1) throw new IOException("Поддерживаются только RAW IP и Ethernet");
        byte[] rec;
        while ((rec=read(in,16,true)) != null) {
            ByteBuffer rh=ByteBuffer.wrap(rec).order(order); long seconds=uint(rh.getInt()), fraction=uint(rh.getInt()), len=uint(rh.getInt()), original=uint(rh.getInt());
            if (fraction >= (nano ? 1000000000L : 1000000L) || len > snap || len > MAX_PACKET || len > original) throw new IOException("Некорректный заголовок пакета");
            byte[] p=read(in,(int)len,false); long t=seconds*1000000L+(nano ? fraction/1000 : fraction);
            if (r.firstMicros < 0 || t < r.firstMicros) r.firstMicros=t; if (t > r.lastMicros) r.lastMicros=t; r.packets++;
            classify(r,p);
        }
        return r;
    }
    private static int u(byte b) { return b & 255; }
    private static void classify(Result r,byte[] p) {
        int pos=0;
        if (r.linkType == 1) { if (p.length < 14) { r.other++; return; } int type=(u(p[12])<<8)|u(p[13]); pos=14; while ((type==0x8100 || type==0x88a8) && p.length>=pos+4) { type=(u(p[pos+2])<<8)|u(p[pos+3]); pos+=4; } if (type!=0x0800 && type!=0x86dd) { r.other++; return; } }
        if (p.length <= pos) {r.other++; return;} int version=u(p[pos])>>4, proto, offset;
        if (version==4 && p.length>=pos+20) { int ihl=(u(p[pos])&15)*4; if(ihl<20 || p.length<pos+ihl) { r.other++; return; } proto=u(p[pos+9]); offset=pos+ihl; if ((u(p[pos+6])&31)!=0 || u(p[pos+7])!=0) {r.other++; return;} }
        else if (version==6 && p.length>=pos+40) {proto=u(p[pos+6]); offset=pos+40;} else {r.other++; return;}
        if (proto==6) r.tcp++;
        else if (proto==17) {r.udp++; if(p.length>=offset+4) {int a=(u(p[offset])<<8)|u(p[offset+1]),b=(u(p[offset+2])<<8)|u(p[offset+3]); if(a==5125||b==5125)r.udp5125++; if(a==5127||b==5127)r.udp5127++;} }
        else r.other++;
    }
}
