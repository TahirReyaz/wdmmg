-- Optional UPI ID (VPA, e.g. name@okhdfcbank) so group members can pay each other from a UPI app.
ALTER TABLE users ADD COLUMN upi_id VARCHAR(100);
