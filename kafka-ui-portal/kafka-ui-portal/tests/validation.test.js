import test from "node:test";
import assert from "node:assert/strict";
import { topicName, integer } from "../server/errors.js";
import { createConfig } from "../server/config.js";
test("rejects internal and malformed topics", () => {
  for (const name of ["__consumer_offsets", "..", "bad topic", "x/y", "", null])
    assert.throws(() => topicName(name));
  assert.equal(topicName("borrower.events.v1"), "borrower.events.v1");
});
test("validates bounded partition and message limits", () => {
  for (const n of [0, -1, 101, 1.5, "oops"])
    assert.throws(() => integer(n, "Limit", 1, 100));
  assert.equal(integer("25", "Limit", 1, 100), 25);
});
test("local ports are distinct and remote brokers are not locally managed", () => {
  assert.throws(() => createConfig({ KAFKA_CONTROL_PORT: "9092" }));
  assert.equal(
    createConfig({ KAFKA_BOOTSTRAP_SERVER: "remote.example:9092" }).local,
    false,
  );
  assert.equal(
    createConfig({ KAFKA_BOOTSTRAP_SERVER: "localhost:29092" }).controllerPort,
    29093,
  );
});
