package server;

import command.*;
import marine.SpaceMarine;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.Set;

class ClientState {
    short toRead = -1;
    boolean messageReady = false;
    ByteBuffer inBuffer;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(baos);
}

public class Server {

    Selector selector;
    ClientState state;
    MarineManager manager;

    SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yy");

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        Server server = new Server(3345);
        server.work();
    }

    private Server(int port) throws IOException {
        selector = Selector.open();
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.bind(new InetSocketAddress(port));
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        manager = new MarineManager();
    }

    private void send() {
        state.out.flush();
        state.messageReady = true;
    }

    private void work() throws IOException, ClassNotFoundException {
        ByteBuffer shortBuffer = ByteBuffer.allocate(2);
        while (true) {
            int readyCount = selector.select();

            if (readyCount == 0) {
                continue;
            }

            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> it = keys.iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();
                it.remove();

                if (key.isAcceptable()) {
                    ServerSocketChannel server = (ServerSocketChannel) key.channel();
                    SocketChannel client = server.accept();
                    client.configureBlocking(false);
                    SelectionKey clientKey = client.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                    clientKey.attach(new ClientState());
                }

                if (key.isReadable()) {
                    SocketChannel client = (SocketChannel) key.channel();
                    state = (ClientState) key.attachment();

                    if (state.toRead == -1) {
                        int r = client.read(shortBuffer);
                        if (r == -1) {
                            client.close();
                            continue;
                        }
                        shortBuffer.flip();
                        state.toRead = shortBuffer.getShort();
                        shortBuffer.clear();
                        state.inBuffer = ByteBuffer.allocate(state.toRead);
                    } else {
                        client.read(state.inBuffer);
                        if (state.inBuffer.position() == state.toRead) {
                            state.inBuffer.flip();
                            byte[] bytes = new byte[state.toRead];
                            state.inBuffer.get(bytes);
                            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                            ObjectInputStream ois = new ObjectInputStream(bais);
                            Command command = (Command) ois.readObject();
                            command.execute(this);
                            send();
                            state.toRead = -1;
                        }
                    }
                }

                if (key.isWritable()) {
                    state = (ClientState) key.attachment();
                    if (state.messageReady) {
                        SocketChannel client = (SocketChannel) key.channel();
                        String message = state.baos.toString();
                        ByteBuffer bb = StandardCharsets.UTF_8.encode(message);
                        shortBuffer.putShort((short) bb.remaining());
                        shortBuffer.flip();
                        client.write(shortBuffer);
                        client.write(bb);
                        shortBuffer.clear();
                        state.messageReady = false;
                        state.baos = new ByteArrayOutputStream();
                        state.out = new PrintStream(state.baos);
                    }
                }
            }
        }
    }
    
    private void printMarine(SpaceMarine marine) {
        state.out.println("ID: " + marine.getId());
        state.out.println("Name: " + marine.getName());
        state.out.println("Coordinates: " + marine.getCoordinates());
        state.out.println("Creation date: " + dateFormat.format(marine.getCreationDate()));
        state.out.println("Health: " + marine.getHealth());
        state.out.println("Category: " + marine.getCategory());
        state.out.println("Weapon type: " + marine.getWeaponType());
        state.out.println("Melee weapon: " + marine.getMeleeWeapon());
        if (marine.getChapter() == null) {
            state.out.println("Chapter: null");
        }
        else {
            state.out.println("Chapter name: " + marine.getChapter().getName());
            state.out.println("Chapter world: " + marine.getChapter().getWorld());
        }
    }
    
    public void executeClear(ClearCommand command) {
        manager.clear();
    }
    
    public void executeFilterGreaterThanCategory(FilterGreaterThanCategoryCommand command) {
        for (SpaceMarine marine : manager.filterGreaterThanCategory(command.category)) {
            printMarine(marine);
        }
    }

    public void executeGroupCountingByCreationDate(GroupCountingByCreationDateCommand command) {
        manager.groupCountingByCreationDate().forEach(
                (date, number) -> state.out.println(dateFormat.format(date) + ": " + number));
    }

    public void executeInfo(InfoCommand command) {
        MarineInfo info = manager.info();
        state.out.println("type: " + info.type);
        state.out.println("number of elements: " + info.n);
        if (info.lastCreatedDate != null) {
            state.out.println("newest marine created on " + dateFormat.format(info.lastCreatedDate));
        }
    }

    public void executeInsert(InsertCommand command) {
        if (!manager.insert(command.key, command.marine)) {
            state.out.println("key already present");
        }
    }

    public void executePrintAscending(PrintAscendingCommand command) {
        for (SpaceMarine marine : manager.ascending()) {
            printMarine(marine);
        }
    }

    public void executeRemoveKey(RemoveKeyCommand command) {
        if (!manager.removeKey(command.key)) {
            state.out.println("key not found");
        }
    }

    public void executeRemoveLower(RemoveLowerCommand command) {
        manager.removeLower(command.marine);
    }

    public void executeRemoveLowerKey(RemoveLowerKeyCommand command) {
        manager.removeLowerKey(command.key);
    }

    public void executeReplaceIfLower(ReplaceIfLowerCommand command) {
        if (!manager.replaceIfLower(command.key, command.marine)) {
            state.out.println("key not found");
        }
    }

    public void executeShow(ShowCommand command) {
        for (SpaceMarine marine : manager.list()) {
            printMarine(marine);
        }
    }

    public void executeUpdate(UpdateCommand command) {
        if (!manager.update(command.id, command.marine)) {
            state.out.println("id not found");
        }
    }
}
