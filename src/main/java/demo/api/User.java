package demo.api;

import lombok.Data;

import java.io.Serializable;

@Data
public class User implements Serializable {
    String name;
    Integer age;
}
