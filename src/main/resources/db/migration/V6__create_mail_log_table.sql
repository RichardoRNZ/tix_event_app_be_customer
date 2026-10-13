CREATE TABLE public.mail_logs (
    id bigserial PRIMARY KEY,
    recipient_email varchar(255) NOT NULL,
    subject varchar(255) NOT NULL,
    status varchar(50) NOT NULL,
    error_message text,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_mail_logs_recipient ON public.mail_logs(recipient_email);
