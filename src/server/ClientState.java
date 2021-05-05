package server;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;

public class ClientState {
    int toRead = -1;
    boolean messageReady = false;
    boolean success = true;
    ByteBuffer inBuffer;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(baos);
}