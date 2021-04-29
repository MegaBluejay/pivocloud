package server;

import message.*;
import marine.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static client.Client.readObject;

public class Server {

    private final Selector selector;
    private ClientState state;
    private final DatabaseManager manager;

    private final Scanner localScanner;

    DateTimeFormatter dateFormatter = DateTimeFormatter.ISO_DATE;

    public static void main(String[] args) throws IOException, ClassNotFoundException, SQLException {
        Server server = new Server(3345);
        server.work();
    }

    private Server(int port) throws IOException, SQLException {
        selector = Selector.open();
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.bind(new InetSocketAddress(port));
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        localScanner = new Scanner(System.in);

        // todo get this elsewhere
        String url = "jdbc:postgresql://localhost:5432/pivo";
        String user = "archer";
        String password = "mmm";

        manager = new DatabaseManager(url, user, password);
    }

    private void send() {
        state.out.flush();
        state.messageReady = true;
    }

    private void work() throws IOException, ClassNotFoundException {
        ByteBuffer shortBuffer = ByteBuffer.allocate(2);
        ByteBuffer intBuffer = ByteBuffer.allocate(4);
        ByteBuffer boolBuffer = ByteBuffer.allocate(1);

        while (true) {
            if (System.in.available() != 0) {
                String line = localScanner.nextLine().trim();
                String[] args = line.split(" +");
                if (args.length > 0) {
                    if (args[0].equals("exit")) {
                        break;
                    } else if (args[0].equals("help")) {
                        System.out.println("exit to exit");
                        System.out.println("help for this message");
                    } else {
                        System.out.println("unknown command");
                    }
                }
            }

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
                        int r = client.read(intBuffer);
                        if (r == -1) {
                            client.close();
                            continue;
                        }
                        intBuffer.flip();
                        state.toRead = intBuffer.getInt();
                        intBuffer.clear();
                        state.inBuffer = ByteBuffer.allocate(state.toRead);
                    } else {
                        client.read(state.inBuffer);
                        if (state.inBuffer.position() == state.toRead) {
                            state.inBuffer.flip();
                            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(state.inBuffer.array()));
                            Request request = (Request) ois.readObject();
                            state.toRead = -1;
                            manager.setCurrentUser(request.user);
                            request.handle(this);
                            send();
                        }
                    }
                }

                if (key.isWritable()) {
                    state = (ClientState) key.attachment();
                    if (state.messageReady) {
                        SocketChannel client = (SocketChannel) key.channel();
                        boolBuffer.put((byte) (state.success ? 1 : 0));
                        boolBuffer.flip();
                        client.write(boolBuffer);
                        boolBuffer.clear();
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
                        state.success = true;
                    }
                }
            }
        }
    }
    
    private void printMarine(Map.Entry<Long, SpaceMarine> entry) {
        Long key = entry.getKey();
        SpaceMarine marine = entry.getValue();
        state.out.println("Owner: " + marine.getOwner());
        state.out.println("Key: " + key);
        state.out.println("ID: " + marine.getId());
        state.out.println("Name: " + marine.getName());
        state.out.println("Coordinates: " + marine.getCoordinates());
        state.out.println("Creation date: " + marine.getCreationDate().format(dateFormatter));
        state.out.println("Health: " + marine.getHealth());
        if (marine.getCategory() != null) {
            state.out.println("Category: " + marine.getCategory());
        }
        state.out.println("Weapon type: " + marine.getWeaponType());
        state.out.println("Melee weapon: " + marine.getMeleeWeapon());
        if (marine.getChapter() != null) {
            state.out.println("Chapter name: " + marine.getChapter().getName());
            if (marine.getChapter().getWorld() != null) {
                state.out.println("Chapter world: " + marine.getChapter().getWorld());
            }
        }
    }

    private static <T> void interDo(List<T> list, Consumer<T> doer, Runnable between) {
        boolean fst = true;
        for (T t : list) {
            if (fst) {
                fst = false;
            }
            else {
                between.run();
            }
            doer.accept(t);
        }
    }

    private void printMarines(List<Map.Entry<Long, SpaceMarine>> entries) {
        interDo(entries, this::printMarine, state.out::println);
    }

    public void handleNormalRequest(NormalRequest request) {
        if (manager.validCreds(request.passHash)) {
            request.command.execute(this);
        } else {
            state.success = false;
        }
    }

    public void handleTestRequest(TestRequest request) {
        if (!manager.validCreds(request.passHash)) {
            state.success = false;
        }
    }

    public void handleRegisterRequest(RegisterRequest request) {
        handleManagerAnswer(manager.addUser(request.passHash), "username taken");
    }

    public void executeClear(ClearCommand command) {
        handleManagerAnswer(manager.clear());
    }
    
    public void executeFilterGreaterThanCategory(FilterGreaterThanCategoryCommand command) {
        printMarines(manager.filterGreaterThanCategory(command.category));
    }

    public void executeGroupCountingByCreationDate(GroupCountingByCreationDateCommand command) {
        manager.groupCountingByCreationDate().forEach(
                (date, number) -> state.out.println(date.format(dateFormatter) + ": " + number));
    }

    public void executeInfo(InfoCommand command) {
        MarineInfo info = manager.info();
        state.out.println("type: " + info.type);
        state.out.println("number of elements: " + info.n);
        if (info.lastCreatedDate != null) {
            state.out.println("newest marine created on " + info.lastCreatedDate.format(dateFormatter));
        }
    }

    private void handleManagerAnswer(ManagerAnswer answer, String errorMessage) {
        if (answer == ManagerAnswer.BAD_OP) {
            state.out.println(errorMessage);
        } else if (answer == ManagerAnswer.BAD_OWNER) {
            state.out.println("can't modify someone else's marine");
        } else if (answer == ManagerAnswer.DB_ERROR) {
            state.out.println("database error");
        }
    }

    private void handleManagerAnswer(ManagerAnswer answer) {
        if (answer == ManagerAnswer.BAD_OP) {
            state.out.println("impossible error");
        } else {
            handleManagerAnswer(answer, "");
        }
    }

    public void executeInsert(InsertCommand command) {
        handleManagerAnswer(manager.insert(command.key, command.marine), "key already present");
    }

    public void executePrintAscending(PrintAscendingCommand command) {
        printMarines(manager.ascending());
    }

    public void executeRemoveKey(RemoveKeyCommand command) {
        handleManagerAnswer(manager.removeKey(command.key), "key not found");
    }

    public void executeRemoveLower(RemoveLowerCommand command) {
        manager.removeLower(command.marine);
    }

    public void executeRemoveLowerKey(RemoveLowerKeyCommand command) {
        manager.removeLowerKey(command.key);
    }

    public void executeReplaceIfLower(ReplaceIfLowerCommand command) {
        handleManagerAnswer(manager.replaceIfLower(command.key, command.marine), "key not found");
    }

    public void executeShow(ShowCommand command) {
        printMarines(manager.list());
    }

    public void executeUpdate(UpdateCommand command) {
        handleManagerAnswer(manager.update(command.id, command.marine), "id not found");
    }
}
