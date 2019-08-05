package com.example.SmartLock3G.tools;

public class Codes {
    public static byte[] CLOSE = {(byte) 0xff, 0x02, 0x01, 0x0A, (byte) 0xfe};//立即关闭
    public static byte[] OPEN = {(byte) 0xff, 0x02, 0x00, 0x0A, (byte) 0xfe};//立即开启

}
