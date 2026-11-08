package com.example.lender_service.service;

import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class JsonService {
  private final JsonMapper json;
  private final Validator validator;

  public <T> T parse(String body, Class<T> type) {
    if (body == null || body.getBytes(StandardCharsets.UTF_8).length > 1048576)
      throw new IllegalArgumentException("Invalid/oversized JSON envelope");
    return validate(json.readValue(body, type));
  }

  public <T> T data(JsonNode data, Class<T> type) {
    if (data == null || !data.isObject())
      throw new IllegalArgumentException("Expected object data");
    return validate(json.treeToValue(data, type));
  }

  public <T> T validate(T value) {
    if (value == null || !validator.validate(value).isEmpty())
      throw new IllegalArgumentException(
          "Invalid " + (value == null ? "payload" : value.getClass().getSimpleName()));
    return value;
  }

  public String write(Object value) {
    return json.writeValueAsString(value);
  }

  public java.util.Map<String, Object> map(JsonNode value) {
    return json.convertValue(
        value, new tools.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
  }

  public JsonNode tree(String value) {
    return json.readTree(value);
  }

  public static String hash(String body) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
