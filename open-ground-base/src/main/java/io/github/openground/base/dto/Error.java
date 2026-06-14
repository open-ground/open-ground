package io.github.openground.base.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * @author open-ground
 */
@Accessors(chain = true)
@Getter
@Setter
@ToString
@NoArgsConstructor
@SuppressWarnings("all")
public class Error implements Serializable {
    private static final long serialVersionUID = 7660756960387438399L;

    private String code; // Error code
    private String message; // Error message
}
