package com.smart.parking.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "parking_spots")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParkingSpot {
    @Id
    private Long id; // We will use 1-10 as IDs matching front-end slot numbers
    private String type; // "regular" or "vip"
    private String status; // "available" or "occupied"
}
