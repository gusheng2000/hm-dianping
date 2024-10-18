package com.hmdp.dto;

import lombok.Data;
import org.springframework.context.ApplicationContext;

@Data
public class LoginFormDTO {
    private String phone;
    private String code;
    private String password;
}
