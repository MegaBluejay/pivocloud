package server;

import marine.SpaceMarine;
import message.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class Server {

    class InputAction extends RecursiveAction {

        private final List<SelectionKey> keys;
        private static final int threshold = 2;

        InputAction(List<SelectionKey> keys) {
            this.keys = keys;
        }

        private List<InputAction> subtasks() {
            List<InputAction> divided = new ArrayList<>();
            divided.add(new InputAction(keys.subList(0, keys.size()/2)));
            divided.add(new InputAction(keys.subList(keys.size()/2, keys.size())));
            return divided;
        }

        private void process() {
            ByteBuffer intBuffer = ByteBuffer.allocate(4);
            keys.forEach(key -> {
                try {
                    SocketChannel client = (SocketChannel) key.channel();
                    ClientState state = (ClientState) key.attachment();

                    synchronized (state) {
                        if (state.toRead == -1) {
                            int r = client.read(intBuffer);
                            if (r == -1) {
                                client.close();
                                return;
                            }
                            intBuffer.flip();
                            state.toRead = intBuffer.getInt();
                            state.inBuffer = ByteBuffer.allocate(state.toRead);
                        } else {
                            client.read(state.inBuffer);
                            if (state.inBuffer.position() == state.toRead) {
                                state.inBuffer.flip();
                                ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(state.inBuffer.array()));
                                state.toRead = -1;
                                Request request = (Request) ois.readObject();
                                request.state = state;
                                Thread thread = new Thread(() -> {
                                    request.handle(Server.this);
                                    state.out.flush();
                                    state.messageReady = true;
                                });
                                thread.start();
                            }
                        }
                    }
                } catch (IOException | ClassNotFoundException ignored) {

                } finally {
                    intBuffer.clear();
                }
            });
        }

        @Override
        protected void compute() {
            if (keys.size() > threshold) {
                ForkJoinTask.invokeAll(subtasks());
            } else {
                process();
            }
        }
    }

    private final Selector selector;
    private final DatabaseManager manager;

    private final Scanner localScanner;

    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ISO_DATE;

    private final ForkJoinPool inPool = ForkJoinPool.commonPool();
    private final Executor outExecutor = Executors.newFixedThreadPool(5);

    public static void main(String[] args) throws IOException, SQLException {
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

    private void work() throws IOException {
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

            List<SelectionKey> readable = new ArrayList<>();

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
                    readable.add(key);
                }

                if (key.isWritable()) {
                    outExecutor.execute(() -> {
                        ClientState state = (ClientState) key.attachment();
                        synchronized (state) {
                            if (state.messageReady) {
                                ByteBuffer boolBuffer = ByteBuffer.allocate(1);
                                ByteBuffer shortBuffer = ByteBuffer.allocate(2);
                                try {
                                    SocketChannel client = (SocketChannel) key.channel();
                                    boolBuffer.put((byte) (state.success ? 1 : 0));
                                    boolBuffer.flip();
                                    client.write(boolBuffer);
                                    String message = state.baos.toString();
                                    ByteBuffer bb = StandardCharsets.UTF_8.encode(message);
                                    shortBuffer.putShort((short) bb.remaining());
                                    shortBuffer.flip();
                                    client.write(shortBuffer);
                                    client.write(bb);
                                } catch (IOException ignored) {
                                } finally {
                                    state.baos = new ByteArrayOutputStream();
                                    state.out = new PrintStream(state.baos);
                                    state.success = true;
                                    state.messageReady = false;
                                }
                            }
                        }
                    });
                }
            }
            if (!readable.isEmpty()) {
                inPool.execute(new InputAction(readable));
            }
        }
    }
    
    private void printMarine(ClientState state, Map.Entry<Long, SpaceMarine> entry) {
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

    private void printMarines(ClientState state, List<Map.Entry<Long, SpaceMarine>> entries) {
        interDo(entries, m -> printMarine(state, m), state.out::println);
    }

    public void handleNormalRequest(NormalRequest request) {
        if (manager.validCreds(request.user, request.passHash)) {
            request.command.state = request.state;
            request.command.execute(this, request.user);
        } else {
            request.state.success = false;
        }
    }

    public void handleTestRequest(TestRequest request) {
        if (!manager.validCreds(request.user, request.passHash)) {
            request.state.success = false;
        }
    }

    public void handleRegisterRequest(RegisterRequest request) {
        handleManagerAnswer(request.state, manager.addUser(request.user, request.passHash), "username taken");
    }

    public void executeClear(ClientState state, String currentUser) {
        handleManagerAnswer(state, manager.clear(currentUser));
    }
    
    public void executeFilterGreaterThanCategory(FilterGreaterThanCategoryCommand command) {
        printMarines(command.state, manager.filterGreaterThanCategory(command.category));
    }

    public void executeGroupCountingByCreationDate(ClientState state) {
        manager.groupCountingByCreationDate().forEach(
                (date, number) -> state.out.println(date.format(dateFormatter) + ": " + number));
    }

    public void executeInfo(ClientState state) {
        MarineInfo info = manager.info();
        state.out.println("type: " + info.type);
        state.out.println("number of elements: " + info.n);
        if (info.lastCreatedDate != null) {
            state.out.println("newest marine created on " + info.lastCreatedDate.format(dateFormatter));
        }
    }

    private void handleManagerAnswer(ClientState state, ManagerAnswer answer, String errorMessage) {
        if (answer == ManagerAnswer.BAD_OP) {
            state.out.println(errorMessage);
        } else if (answer == ManagerAnswer.BAD_OWNER) {
            state.out.println("can't modify someone else's marine");
        } else if (answer == ManagerAnswer.DB_ERROR) {
            state.out.println("database error");
        }
    }

    private void handleManagerAnswer(ClientState state, ManagerAnswer answer) {
        if (answer == ManagerAnswer.BAD_OP) {
            state.out.println("impossible error");
        } else {
            handleManagerAnswer(state, answer, "");
        }
    }

    public void executeInsert(String currentUser, InsertCommand command) {
        handleManagerAnswer(command.state, manager.insert(currentUser, command.key, command.marine), "key already present");
    }

    public void executePrintAscending(ClientState state) {
        printMarines(state, manager.ascending());
    }

    public void executeRemoveKey(String currentUser, RemoveKeyCommand command) {
        handleManagerAnswer(command.state, manager.removeKey(currentUser, command.key), "key not found");
    }

    public void executeRemoveLower(String currentUser, RemoveLowerCommand command) {
        handleManagerAnswer(command.state, manager.removeLower(currentUser, command.marine));
    }

    public void executeRemoveLowerKey(String currentUser, RemoveLowerKeyCommand command) {
        handleManagerAnswer(command.state, manager.removeLowerKey(currentUser, command.key));
    }

    public void executeReplaceIfLower(String currentUser, ReplaceIfLowerCommand command) {
        handleManagerAnswer(command.state, manager.replaceIfLower(currentUser, command.key, command.marine), "key not found");
    }

    public void executeShow(ClientState state) {
        printMarines(state, manager.list());
    }

    public void executeUpdate(String currentUser, UpdateCommand command) {
        handleManagerAnswer(command.state, manager.update(currentUser, command.id, command.marine), "id not found");
    }
}
