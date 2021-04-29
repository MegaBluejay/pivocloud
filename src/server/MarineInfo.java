package server;

import java.time.LocalDate;

class MarineInfo {
    String type;
    int n;
    LocalDate lastCreatedDate;

    public MarineInfo(String type, int n, LocalDate lastCreatedDate) {
        this.type = type;
        this.n = n;
        this.lastCreatedDate = lastCreatedDate;
    }
}
