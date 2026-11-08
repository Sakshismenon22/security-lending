export class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}
export function topicName(value) {
  if (
    typeof value !== "string" ||
    !/^[a-zA-Z0-9._-]{1,249}$/.test(value) ||
    value === "." ||
    value === ".."
  ) {
    throw new HttpError(
      400,
      "Use 1–249 letters, numbers, periods, underscores, or hyphens for the topic name.",
    );
  }
  if (value.startsWith("__"))
    throw new HttpError(
      400,
      "Kafka internal topics are read-only in this portal.",
    );
  return value;
}
export function integer(value, name, min, max) {
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < min || parsed > max)
    throw new HttpError(400, `${name} must be between ${min} and ${max}.`);
  return parsed;
}
