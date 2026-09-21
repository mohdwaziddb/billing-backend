package com.billing.util;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class MobileResponseDTOFactory {

    public ResponseEntity<MobileResponseDTO> reportInternalServerError(Throwable e) {
        MobileResponseDTO dto = new MobileResponseDTO();
        dto.setCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        dto.setMessage("Some unknown error occured.");
        System.out.println("The cause of exception : " + e.getCause());
        System.err.println("The cause of exception : " + e.getCause());
        e.printStackTrace();
        return new ResponseEntity<>(dto, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public ResponseEntity<MobileResponseDTO> successMessage(String message) {
        MobileResponseDTO dto = new MobileResponseDTO();
        dto.setCode(HttpStatus.OK.value());
        dto.setMessage(message);
        dto.setSuccess(true);
        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    public ResponseEntity<MobileResponseDTO> failedMessage(String message) {
        MobileResponseDTO dto = new MobileResponseDTO();
        dto.setCode(HttpStatus.UNPROCESSABLE_ENTITY.value());
        dto.setMessage(message);
        return new ResponseEntity<>(dto, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public ResponseEntity<MobileResponseDTO> failedMessage(String message, HttpStatus status) {
        MobileResponseDTO dto = new MobileResponseDTO();
        dto.setCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        dto.setMessage(message);
        return new ResponseEntity<>(dto, status);
    }
}
