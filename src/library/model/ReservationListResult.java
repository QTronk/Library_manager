package library.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/** Phần "data" của action GET_MY_RESERVATIONS (API.md mục 3.5.3): { danhSach: [ ... ] }. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReservationListResult {
    public List<Reservation> danhSach = new ArrayList<>();
}
