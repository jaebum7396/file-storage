package com.codism.model.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class File {
    private String imageLocation;
    private List<String> imageLocations;

    public String getImageLocation() {
        return imageLocation;
    }

    public void setImageLocation(String imageLocation) {
        this.imageLocation = imageLocation;
    }

    public List<String> getImageLocations() {
        return imageLocations;
    }
}