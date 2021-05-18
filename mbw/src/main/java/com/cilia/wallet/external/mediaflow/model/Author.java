package com.cilia.wallet.external.mediaflow.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public class Author implements Serializable {
    public Author(String name) {
        this.name = name;
    }

    public Author() {
    }

    @JsonProperty("id")
    public int id;

    @JsonProperty("name")
    public String name;
}
