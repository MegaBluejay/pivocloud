package server;

import marine.AstartesCategory;
import marine.SpaceMarine;

import java.util.*;
import java.util.stream.Collectors;

class MarineInfo {
    String type;
    int n;
    Date lastCreatedDate;

    public MarineInfo(String type, int n, Date lastCreatedDate) {
        this.type = type;
        this.n = n;
        this.lastCreatedDate = lastCreatedDate;
    }
}

enum ManagerAnswer {
    OK,
    BAD_OP,
    BAD_OWNER
}

public class MarineManager {
    private Map<Long, SpaceMarine> marines;

    private long maxid = 0;

    private String currentUser;

    public MarineManager(Map<Long, SpaceMarine> marines) {
        this.marines = marines;
    }

    public void setCurrentUser(String user) {
        this.currentUser = user;
    }

    private boolean isCurrentUsers(SpaceMarine marine) {
        return marine.getOwner().equals(currentUser);
    }

    public MarineInfo info() {
        String type = "HashMap<Long, SpaceMarine>";
        int n = marines.size();
        Date latestCreatedDate = marines.values()
                .stream().map(SpaceMarine::getCreationDate).max(Comparator.naturalOrder()).orElse(null);
        return new MarineInfo(type, n, latestCreatedDate);
    }

    public List<Map.Entry<Long, SpaceMarine>> list() {
        return new ArrayList<>(marines.entrySet());
    }

    public ManagerAnswer insert(Long key, SpaceMarine marine) {
        if (marines.containsKey(key)) {
            return ManagerAnswer.BAD_OP;
        }
        marine.setId(++maxid);
        marine.setCreationDate(new Date());
        marines.put(key, marine);
        return ManagerAnswer.OK;
    }

    public ManagerAnswer update(Long id, SpaceMarine marine) {
        Optional<Long> mbKey = marines.keySet()
                .stream().filter(k -> marines.get(k).getId().equals(id))
                .findAny();
        if (mbKey.isPresent()) {
            Long key = mbKey.get();
            SpaceMarine old = marines.get(key);
            if (!isCurrentUsers(old)) {
                return ManagerAnswer.BAD_OWNER;
            }
            marine.setId(id);
            marine.setCreationDate(old.getCreationDate());
            marines.put(key, marine);
            return ManagerAnswer.OK;
        }
        return ManagerAnswer.BAD_OP;
    }

    public ManagerAnswer removeKey(Long key) {
        if (marines.containsKey(key)) {
            if (!isCurrentUsers(marines.get(key))) {
                return ManagerAnswer.BAD_OWNER;
            }
            marines.remove(key);
            return ManagerAnswer.OK;
        }
        return ManagerAnswer.BAD_OP;
    }

    public void clear() {
        marines.keySet()
                .stream().filter(k -> isCurrentUsers(marines.get(k)))
                .forEach(marines::remove);
    }

    public void removeLower(SpaceMarine marine) {
        marines.keySet()
                .stream().filter(k -> marines.get(k).compareTo(marine) < 0)
                .filter(k -> isCurrentUsers(marines.get(k)))
                .forEach(marines::remove);
    }

    public ManagerAnswer replaceIfLower(Long key, SpaceMarine marine) {
        if (marines.containsKey(key)) {
            SpaceMarine old = marines.get(key);
            if (!isCurrentUsers(old)) {
                return ManagerAnswer.BAD_OWNER;
            }
            if (old.compareTo(marine) < 0) {
                marine.setId(old.getId());
                marine.setCreationDate(old.getCreationDate());
                marines.put(key, marine);
            }
            return ManagerAnswer.OK;
        }
        return ManagerAnswer.BAD_OP;
    }

    public void removeLowerKey(Long key) {
        marines.keySet()
                .stream().filter(k -> k < key)
                .filter(k -> isCurrentUsers(marines.get(k)))
                .forEach(marines::remove);
    }

    public Map<Date, Long> groupCountingByCreationDate() {
        return marines.values()
                .stream().collect(Collectors.groupingBy(
                        SpaceMarine::getCreationDate, Collectors.counting()));
    }

    public List<Map.Entry<Long, SpaceMarine>> filterGreaterThanCategory(AstartesCategory category) {
        return marines.entrySet().stream()
                .filter(e -> {
                    AstartesCategory cat = e.getValue().getCategory();
                    return cat != null && cat.ordinal() > category.ordinal();
                }).collect(Collectors.toList());
    }

    public List<Map.Entry<Long, SpaceMarine>> ascending() {
        return marines.entrySet().stream().sorted(Map.Entry.comparingByValue()).collect(Collectors.toList());
    }
}
