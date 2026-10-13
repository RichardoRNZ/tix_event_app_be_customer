CREATE TABLE public.email_otps (
	id bigserial NOT NULL,
	user_id int8 NOT NULL,
	otp_hash varchar(255) NOT NULL,
	expires_at timestamp NOT NULL,
	attempts int4 NOT NULL DEFAULT 0,
	used_at timestamp NULL,
	created_date timestamp DEFAULT CURRENT_TIMESTAMP NULL,
	CONSTRAINT email_otps_pkey PRIMARY KEY (id),
	CONSTRAINT email_otps_user_fk FOREIGN KEY (user_id) REFERENCES public.users(id)
);
CREATE INDEX idx_email_otps_user_id ON public.email_otps(user_id);