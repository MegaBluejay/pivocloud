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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static client.Client.readObject;

class ClientState {
    int toRead = -1;
    boolean messageReady = false;
    boolean success = true;
    ByteBuffer inBuffer;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(baos);
}

public class Server {

    private final Selector selector;
    private ClientState state;
    private final MarineManager manager;

    private String saveFilePath = System.getenv("FILE");
    private final Scanner localScanner;

    SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yy");

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        Server server = new Server(3345);
        server.work();
    }

    private static String readLine(InputStreamReader reader) throws IOException {
        StringBuilder builder = new StringBuilder();
        int ci;
        while ((ci = reader.read()) != -1) {
            char c = (char) ci;
            if (c == '\n') {
                break;
            }
            builder.append(c);
        }
        return builder.toString();
    }

    private Map<Integer, String> readFile(Map<Long, SpaceMarine> marines) throws CustomFileException {
        if (saveFilePath == null) {
            throw CustomFileException.envVarNotSet();
        }
        InputStream stream;
        try {
            stream = new FileInputStream(saveFilePath);
        } catch (FileNotFoundException e) {
            throw CustomFileException.notFound();
        }

        InputStreamReader reader = new InputStreamReader(stream);
        String line;
        int i = 1;
        Map<Integer, String> errors = new HashMap<>();

        while (true) {
            try {
                if ((line = readLine(reader)).isEmpty()) break;
            } catch (IOException e) {
                throw CustomFileException.readProblem();
            }

            String[] ogFields = line.split(" *, *");
            if (Arrays.stream(ogFields).anyMatch(s -> !(s.startsWith("\"") == s.endsWith("\"")))) {
                errors.put(i++, "bad quoting");
                continue;
            }

            String[] fields = Arrays.stream(ogFields).map(s -> {
                if (s.equals("null")) {
                    return null;
                }
                return s.replaceAll("^\"|\"$", "");
            }).toArray(String[]::new);

            if (fields.length != 12) {
                errors.put(i++, "only " + fields.length + " fields");
                continue;
            }

            long key;
            try {
                key = Long.parseLong(fields[0]);
            } catch (NumberFormatException e) {
                errors.put(i++, "invalid key");
                continue;
            }

            long id;
            try {
                id = Long.parseLong(fields[1]);
            } catch (NumberFormatException e) {
                errors.put(i++, "invalid id");
                continue;
            }

            String name = fields[2];
            if (name.isEmpty()) {
                errors.put(i++, "invalid name");
                continue;
            }

            double x;
            double y;
            try {
                x = Double.parseDouble(fields[3]);
            } catch (NumberFormatException e) {
                errors.put(i++, "invalid x coordinate");
                continue;
            }
            try {
                y = Double.parseDouble(fields[4]);
            } catch (NumberFormatException e) {
                errors.put(i++, "invalid y coordinate");
                continue;
            }
            Coordinates coordinates = new Coordinates(x, y);

            Date creationDate;
            try {
                creationDate = dateFormat.parse(fields[5]);
            } catch (ParseException e) {
                errors.put(i++, "invalid date");
                continue;
            }

            float health;
            try {
                health = Float.parseFloat(fields[6]);
                if (health <= 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                errors.put(i++, "invalid health");
                continue;
            }

            AstartesCategory category;
            if (fields[7] == null) {
                category = null;
            } else {
                try {
                    category = AstartesCategory.valueOf(fields[7]);
                } catch (IllegalArgumentException e) {
                    errors.put(i++, "invalid category");
                    continue;
                }
            }

            Weapon weapon;
            try {
                weapon = Weapon.valueOf(fields[8]);
            } catch (IllegalArgumentException e) {
                errors.put(i++, "invalid weapon type");
                continue;
            }

            MeleeWeapon meleeWeapon;
            try {
                meleeWeapon = MeleeWeapon.valueOf(fields[9]);
            } catch (IllegalArgumentException e) {
                errors.put(i++, "invalid melee weapon type");
                continue;
            }

            Chapter chapter;
            if (fields[10] == null) {
                chapter = null;
            } else {
                String chapterName = fields[10];
                String world = fields[11];
                chapter = new Chapter(chapterName, world);
            }

            marines.put(key, new SpaceMarine(id, name, coordinates, creationDate, health, category, weapon, meleeWeapon, chapter));
            i++;
        }
        return errors;
    }

    private static String quotedToString(Object o) {
        if (o == null) {
            return "null";
        }
        return "\"" + o + "\"";
    }

    private Server(int port) throws IOException {
        selector = Selector.open();
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.bind(new InetSocketAddress(port));
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        localScanner = new Scanner(System.in);

        Map<Long, SpaceMarine> marines = new HashMap<>();

        try {
            Map<Integer, String> errors = readFile(marines);
            if (!errors.isEmpty()) {
                System.out.println("Errors in file: ");
                errors.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e ->
                        System.out.println("line " + e.getKey() + ": " + e.getValue()));
            }
        } catch (CustomFileException e) {
            System.out.println("Couldn't read file: " + e.getMessage());
        }

        manager = new MarineManager(marines);
    }

    private void send() {
        state.out.flush();
        state.messageReady = true;
    }

    private void save() {
        File file = new File(saveFilePath);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                System.out.println("can't create file");
                newPath();
                return;
            }
        }
        if (file.canWrite()) {
            try {
                PrintWriter writer = new PrintWriter(saveFilePath);
                List<Map.Entry<Long, SpaceMarine>> entries = manager.list();
                entries.forEach(e -> {
                    Long key = e.getKey();
                    SpaceMarine m = e.getValue();
                    String line = Stream.of(key, m.getId(), m.getName(), m.getCoordinates().getX(),
                            m.getCoordinates().getY(), dateFormat.format(m.getCreationDate()),
                            m.getHealth(), m.getCategory(), m.getWeaponType(), m.getMeleeWeapon(),
                            Optional.ofNullable(m.getChapter()).map(Chapter::getName).orElse(null),
                            Optional.ofNullable(m.getChapter()).map(Chapter::getWorld).orElse(null))
                            .map(Server::quotedToString).collect(Collectors.joining(", "));
                    writer.println(line);
                });
                writer.close();
            } catch (FileNotFoundException e) {
                System.out.println("impossible error");
            }
        } else {
            System.out.println("bad permissions");
            newPath();
        }
    }

    private void newPath() {
        Boolean doSave = readObject(
                localScanner,
                false,
                s -> {
                    if (s.equals("y")) {
                        return true;
                    } else if (s.equals("n")) {
                        return false;
                    } else {
                        throw new IllegalArgumentException();
                    }
                },
                ds -> true,
                "Do you want to set a new save file path (y/n): ",
                "enter 'y' or 'n'",
                false
        );

        if (doSave) {
            String path = readObject(
                    localScanner,
                    false,
                    s -> {
                        try {
                            File file = new File(s);
                            boolean wasCreated = file.createNewFile();
                            if (wasCreated || Files.isWritable(file.toPath())) {
                                return s;
                            }
                        } catch (IOException e) {
                            throw new IllegalArgumentException();
                        }
                        throw new IllegalArgumentException();
                    },
                    np -> true,
                    "Provide a new save file location or leave empty to leave as is: ",
                    "not a valid path",
                    true
            );

            if (path != null) {
                saveFilePath = path;
            }
        }
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
                    if (args[0].equals("save")) {
                        save();
                    } else if (args[0].equals("exit")) {
                        save();
                        break;
                    } else if (args[0].equals("help")) {
                        System.out.println("save to save");
                        System.out.println("exit to save and exit");
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
        state.out.println("Key: " + key);
        state.out.println("ID: " + marine.getId());
        state.out.println("Name: " + marine.getName());
        state.out.println("Coordinates: " + marine.getCoordinates());
        state.out.println("Creation date: " + dateFormat.format(marine.getCreationDate()));
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
        interDo(entries, this::printMarine, System.out::println);
    }

    public void handleNormalRequest(NormalRequest request) {
        if (users.containsKey(request.user) && users.get(request.user).equals(request.passHash)) {
            request.command.execute(this);
        } else {
            state.success = false;
        }
    }

    public void handleTestRequest(TestRequest request) {
        if (!(users.containsKey(request.user) && users.get(request.user).equals(request.passHash))) {
            state.success = false;
        }
    }

    Map<String, String> users = new HashMap<>();
    public void handleRegisterRequest(RegisterRequest request) {
        if (users.containsKey(request.user)) {
            state.out.println("username already taken");
            state.success = false;
        } else {
            users.put(request.user, request.passHash);
            state.out.println("registered");
        }
    }

    public void executeClear(ClearCommand command) {
        manager.clear();
    }
    
    public void executeFilterGreaterThanCategory(FilterGreaterThanCategoryCommand command) {
        printMarines(manager.filterGreaterThanCategory(command.category));
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
        printMarines(manager.ascending());
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
        printMarines(manager.list());
    }

    public void executeUpdate(UpdateCommand command) {
        if (!manager.update(command.id, command.marine)) {
            state.out.println("id not found");
        }
    }
}
