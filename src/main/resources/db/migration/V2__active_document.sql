create table active_document (
  session_id varchar(128) primary key,
  document_id varchar(256) not null,
  title varchar(512),
  source varchar(1024),
  source_token varchar(256),
  created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create index idx_active_document_source_token
  on active_document(source_token);
