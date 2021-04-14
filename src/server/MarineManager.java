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

public class MarineManager {
    private Map<Long, SpaceMarine> marines;

    private long maxid = 0;

    public MarineManager(Map<Long, SpaceMarine> marines) {
        this.marines = marines;
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

    public boolean insert(Long key, SpaceMarine marine) {
        if (marines.containsKey(key)) {
            return false;
        }
        marine.setId(++maxid);
        marine.setCreationDate(new Date());
        marines.put(key, marine);
        return true;
    }

    public boolean update(Long id, SpaceMarine marine) {
        Optional<Long> mbKey = marines.keySet()
                .stream().filter(k -> marines.get(k).getId().equals(id))
                .findAny();
        if (mbKey.isPresent()) {
            Long key = mbKey.get();
            SpaceMarine old = marines.get(key);
            marine.setId(id);
            marine.setCreationDate(old.getCreationDate());
            marines.put(key, marine);
            return true;
        }
        return false;
    }

    public boolean removeKey(Long key) {
        if (marines.containsKey(key)) {
            marines.remove(key);
            return true;
        }
        return false;
    }

    public void clear() {
        marines.clear();
    }

    public void removeLower(SpaceMarine marine) {
        marines.keySet()
                .stream().filter(k -> marines.get(k).compareTo(marine) < 0)
                .forEach(marines::remove);
    }

    public boolean replaceIfLower(Long key, SpaceMarine marine) {
        if (marines.containsKey(key)) {
            SpaceMarine old = marines.get(key);
            if (old.compareTo(marine) < 0) {
                marine.setId(old.getId());
                marine.setCreationDate(old.getCreationDate());
                marines.put(key, marine);
            }
            return true;
        }
        return false;
    }

    public void removeLowerKey(Long key) {
        marines.keySet()
                .stream().filter(k -> k < key)
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
