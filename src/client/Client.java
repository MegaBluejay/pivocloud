package client;

import message.*;
import marine.*;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Client {

    boolean exit = false;
    boolean inScript = false;

    String host;
    int port;
    Socket socket;
    DataInputStream dis;
    OutputStream os;
    String user;
    RequestFactory requestFactory = null;

    private static String md2(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD2");
            byte[] messageDigest = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(messageDigest.length*2);
            for(byte b: messageDigest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
    }

    private void login(Scanner scanner, boolean quiet) {
        while (true) {
            Boolean needRegister = readObject(
                    scanner,
                    quiet,
                    s -> {
                        s = s.toLowerCase();
                        if (s.equals("l") || s.isEmpty()) {
                            return false;
                        } else if (s.equals("r")) {
                            return true;
                        } else {
                            throw new IllegalArgumentException();
                        }
                    },
                    nr -> true,
                    "Do you want to login or register? ([L]/r) ",
                    "enter 'l', 'r', or leave empty for default",
                    false
            );

            String user = readObject(
                    scanner,
                    quiet,
                    s -> s,
                    u -> true,
                    "Enter username: ",
                    "",
                    false
            );
            String password = readObject(
                    scanner,
                    quiet,
                    s -> s,
                    p -> true,
                    "Enter password: ",
                    "",
                    false
            );
            String passHash = md2(password);

            Response response;
            if (needRegister) {
                response = getResonse(new RegisterRequest(user, passHash));
            } else {
                response = getResonse(new TestRequest(user, passHash));
            }
            printResonse(response);
            if (response.success) {
                this.user = user;
                requestFactory = new RequestFactory(user, passHash);
                return;
            }
        }
    }

    private boolean simpleZeroArg(String[] args, String commandName) {
        if (args.length > 1) {
            System.out.println(commandName + " doesn't take any same-line arguments");;
            return false;
        }
        return true;
    }

    private <T> Optional<T> simpleSingleArg(String[] args, Function<String, T> parse, String commandName, String argName) {
        if (args.length == 1) {
            System.out.println(argName + " required");
        }
        else if (args.length > 2) {
            System.out.println(commandName + " only takes 1 same-line argument");
        }
        else {
            try {
                return Optional.of(parse.apply(args[1]));
            } catch (Exception e) {
                System.out.println("invalid " + argName);
            }
        }
        return Optional.empty();
    }

    public static <T> T readObject(Scanner scanner, boolean quiet, Function<String, T> conv, Predicate<T> isValid, String promptMessage, String errorMessage, boolean canBeEmpty) {
        while (true) {
            if (!quiet) {
                System.out.print(promptMessage);
            }
            if (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (canBeEmpty && line.isEmpty()) {
                    return null;
                }
                try {
                    T t = conv.apply(line);
                    if (isValid.test(t)) {
                        return t;
                    }
                } catch (Exception ignored) {
                }
                System.out.println(errorMessage);
            } else {
                System.exit(0);
            }
        }
    }

    private SpaceMarine readMarine(Scanner scanner, boolean quiet) {
        System.out.println("Note: all decimal fractions are stored with limited precision and may be rounded from the value given");

        Long id = null;

        String name = readObject(
                scanner,
                quiet,
                s -> s,
                s -> !s.isEmpty(),
                "Enter name: ",
                "name can't be empty",
                false);

        Double x = readObject(
                scanner,
                quiet,
                Double::parseDouble,
                d -> true,
                "Enter x coordinate (decimal fraction): ",
                "not a valid coordinate",
                false
        );

        Double y = readObject(
                scanner,
                quiet,
                Double::parseDouble,
                d -> true,
                "Enter y coordinate (decimal fraction): ",
                "not a valid coordinate",
                false
        );

        Coordinates coordinates = new Coordinates(x,y);

        LocalDate date = null;

        Float health = readObject(
                scanner,
                quiet,
                Float::parseFloat,
                f -> f > 0,
                "Enter health (decimal fraction, must be >0): ",
                "not a valid health value",
                false
        );

        AstartesCategory category = readObject(
                scanner,
                quiet,
                AstartesCategory::valueOf,
                c -> true,
                "Enter a category (one of [" + Arrays.stream(AstartesCategory.values())
                        .map(AstartesCategory::toString)
                        .collect(Collectors.joining(", ")) + "]) or leave empty: ",
                "not a valid category",
                true
        );

        Weapon weapon = readObject(
                scanner,
                quiet,
                Weapon::valueOf,
                w -> true,
                "Enter a weapong type (one of [" + Arrays.stream(Weapon.values())
                        .map(Weapon::toString)
                        .collect(Collectors.joining(", ")) + "]): ",
                "not a valid weapon type",
                false
        );

        MeleeWeapon meleeWeapon = readObject(
                scanner,
                quiet,
                MeleeWeapon::valueOf,
                mw -> true,
                "Enter a melee weapon type (one of [" + Arrays.stream(MeleeWeapon.values())
                        .map(MeleeWeapon::toString)
                        .collect(Collectors.joining(", ")) + "]): ",
                "not a valid melee weapon type",
                false
        );

        Boolean needChapter = readObject(
                scanner,
                quiet,
                s -> {
                    if (s.equals("y")) {
                        return true;
                    } else if (s.equals("n")) {
                        return false;
                    } else {
                        throw new IllegalArgumentException();
                    }
                },
                nc -> true,
                "Do you want to add a chapter (y/n): ",
                "enter 'y' or 'n'",
                false
        );

        Chapter chapter = null;
        if (needChapter) {
            String chapterName = readObject(
                    scanner,
                    quiet,
                    cn -> cn,
                    s -> !s.isEmpty(),
                    "Enter chapter name: ",
                    "chapter name can't be empty",
                    false
            );

            String world = readObject(
                    scanner,
                    quiet,
                    w -> w,
                    w -> true,
                    "Enter world name or leave empty: ",
                    "",
                    true
            );
            chapter = new Chapter(chapterName, world);
        }
        return new SpaceMarine(id, name, coordinates, date, health, category, weapon, meleeWeapon, chapter, user);
    }

    private Optional<Command> readCommand(Scanner scanner, boolean quiet) {
        if (!quiet) {
            System.out.print("> ");
        }
        if (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            String[] args = line.trim().split(" +");
            if (args.length > 0) {
                String command = args[0];
                if (command.equals("help")) {
                    System.out.println("all args written as {arg} must be specified on further lines");
                    System.out.println("help print help");
                    System.out.println("info print info about current state of marines");
                    System.out.println("show print all marines");
                    System.out.println("insert key {marine} add new marine with given key");
                    System.out.println("update id {marine} update marine with given id");
                    System.out.println("remove_key key delete marine with given key");
                    System.out.println("clear delete all marines");
                    System.out.println("execute_script file_name execute script");
                    System.out.println("exit end execution");
                    System.out.println("remove_lower {marine} delete all marines with health lower than the one given");
                    System.out.println("replace_if_lower key {marine} replace marine with key with given one if the new health is lower than the old");
                    System.out.println("remove_lower_key key delete all marines with key lower than given");
                    System.out.println("group_counting_by_creation_date print number of marines with each creation date");
                    System.out.println("filter_greater_than_category {category} print marines with categories higher than the one given");
                    System.out.println("print_ascending print all marines sorted by health");
                    return Optional.empty();
                } else if (command.equals("info")) {
                    return Optional.of(new InfoCommand());
                } else if (command.equals("show")) {
                    return Optional.of(new ShowCommand());
                } else if (command.equals("insert")) {
                    return simpleSingleArg(args,
                            Long::parseLong,
                            "insert",
                            "key").map(k -> new InsertCommand(k, readMarine(scanner, quiet)));
                } else if (command.equals("update")) {
                    return simpleSingleArg(args,
                            Long::parseLong,
                            "update",
                            "id").map(id -> new UpdateCommand(id, readMarine(scanner, quiet)));
                } else if (command.equals("remove_key")) {
                    return simpleSingleArg(args,
                            Long::parseLong,
                            "remove_key",
                            "key").map(RemoveKeyCommand::new);
                } else if (command.equals("clear")) {
                    return Optional.of(new ClearCommand());
                } else if (command.equals("execute_script")) {
                    if (args.length == 1) {
                        System.out.println("file required");
                    } else if (args.length > 2) {
                        System.out.println("execute_script only takes 1 argument");
                    } else if (inScript) {
                        System.out.println("script recursion not allowed, skipping execute_script");
                    } else {
                        File scriptFile = new File(args[1]);
                        try {
                            if (Files.isReadable(scriptFile.toPath())) {
                                Scanner scriptScanner = new Scanner(new FileInputStream(scriptFile));
                                inScript = true;
                                work(scriptScanner, true);
                                inScript = false;
                            } else {
                                System.out.println("file not readable");
                            }
                        } catch (FileNotFoundException e) {
                            System.out.println("file not found");
                        }
                    }
                    return Optional.empty();
                } else if (command.equals("exit")) {
                    exit = true;
                    return Optional.empty();
                } else if (command.equals("remove_lower")) {
                    if (simpleZeroArg(args, "remove_lower")) {
                        return Optional.of(new RemoveLowerCommand(readMarine(scanner, quiet)));
                    } else {
                        return Optional.empty();
                    }
                } else if (command.equals("replace_if_lower")) {
                    return simpleSingleArg(args,
                            Long::parseLong,
                            "replace_if_lower",
                            "key").map(k -> new ReplaceIfLowerCommand(k, readMarine(scanner, quiet)));
                } else if (command.equals("remove_lower_key")) {
                    return simpleSingleArg(args,
                            Long::parseLong,
                            "remove_lower_key",
                            "key").map(RemoveLowerKeyCommand::new);
                } else if (command.equals("group_counting_by_creation_date")) {
                    return Optional.of(new GroupCountingByCreationDateCommand());
                } else if (command.equals("filter_greater_than_category")) {
                    if (args.length > 1) {
                        System.out.println("filter_greater_than_category doesn't take any same-line arguments");
                        return Optional.empty();
                    } else {
                        AstartesCategory category = readObject(
                                scanner,
                                quiet,
                                AstartesCategory::valueOf,
                                c -> true,
                                "Enter category (one of [" +
                                        Arrays.stream(AstartesCategory.values()).map(AstartesCategory::toString)
                                                .collect(Collectors.joining(", ")) + "]): ",
                                "invalid category",
                                false
                        );
                        return Optional.of(new FilterGreaterThanCategoryCommand(category));
                    }
                } else if (command.equals("print_ascending")) {
                    return Optional.of(new PrintAscendingCommand());
                } else {
                    System.out.println("unknown command");
                    return Optional.empty();
                }
            }
        } else {
            exit = true;
        }
        return Optional.empty();
    }

    private void printResonse(Response response) {
        if (!response.success) {
            System.out.println("bad credentials");
        }
        else if (!response.response.isEmpty()) {
            System.out.print(response.response);
        }
    }

    private Response getResonse(Request request) {
        while (true) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                for (int i = 0; i < 4; i++) {
                    baos.write(0);
                }
                ObjectOutputStream oos = new ObjectOutputStream(baos);
                oos.writeObject(request);
                oos.flush();
                ByteBuffer bb = ByteBuffer.wrap(baos.toByteArray());
                bb.putInt(0, baos.size()-4);

                String res;
                boolean success;

                socket.getOutputStream().write(bb.array());
                socket.getOutputStream().flush();
                success = dis.readBoolean();
                res = dis.readUTF();
                return new Response(success, res);
            } catch (IOException e) {
                connect();
            }
        }
    }

    private void connect() {
        boolean firstTry = true;
        while (true) {
            try {
                socket = new Socket(host, port);
                os = socket.getOutputStream();
                dis = new DataInputStream(socket.getInputStream());
                if (!firstTry) {
                    System.out.println("connected");
                }
                break;
            } catch (IOException e) {
                System.out.println("error, reconnecting");
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                firstTry = false;
            }
        }
    }

    private void work(Scanner scanner, boolean quiet) {
        if (requestFactory == null) {
            login(scanner, quiet);
        }
        while (!exit) {
            Optional<Command> mbCommand = readCommand(scanner, quiet);
            mbCommand.map(requestFactory::request)
                    .map(this::getResonse).ifPresent(this::printResonse);
        }
    }

    private Client(String host, int port) {
        this.host = host;
        this.port = port;
        connect();
    }

    public static void main(String[] args) throws IOException {
        Client client = new Client("localhost", 3345);

        client.work(new Scanner(System.in), false);
    }
}
