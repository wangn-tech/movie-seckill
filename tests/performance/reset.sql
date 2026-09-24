SET @schedule_id = 9001;

DELETE os FROM order_seat os
JOIN ticket_order o ON o.id = os.order_id
WHERE o.schedule_id = @schedule_id;
DELETE FROM seat_lock WHERE schedule_id = @schedule_id;
DELETE FROM ticket_order WHERE schedule_id = @schedule_id;
DELETE FROM outbox_event WHERE schedule_id = @schedule_id;
UPDATE movie_schedule
SET available_seats = total_seats, version = version + 1
WHERE id = @schedule_id;
