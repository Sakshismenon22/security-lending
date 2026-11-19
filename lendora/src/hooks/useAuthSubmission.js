import { useEffect, useRef, useState } from "react";
export default function useAuthSubmission(operation) {
  const controller = useRef(null);
  const [pending, setPending] = useState(false),
    [error, setError] = useState("");
  useEffect(() => () => controller.current?.abort(), []);
  async function submit(values) {
    if (controller.current) return null;
    const request = new AbortController();
    controller.current = request;
    setPending(true);
    setError("");
    try {
      const result = await operation(values, request.signal);
      return request.signal.aborted ? null : result;
    } catch (error) {
      if (!request.signal.aborted) setError(error.message);
      return null;
    } finally {
      if (!request.signal.aborted) setPending(false);
      if (controller.current === request) controller.current = null;
    }
  }
  return { submit, pending, error };
}
