UPDATE outbound_message SET state='FAILED' WHERE state='SUSPENDED';
UPDATE kafka_publication SET state='FAILED' WHERE state='SUSPENDED';
