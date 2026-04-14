create table conversation_thread (
  session_id varchar(128) primary key,
  summary_topic varchar(512),
  summary_text text,
  summary_version int not null default 0,
  created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table conversation_message (
  id bigint not null auto_increment primary key,
  message_id varchar(36) not null,
  session_id varchar(128) not null,
  turn_no int not null,
  role varchar(16) not null,
  source_type varchar(32) not null default 'CHAT',
  content text not null,
  created_at timestamp(6) not null default current_timestamp(6),
  constraint uq_conversation_message_message_id unique (message_id),
  constraint fk_conversation_message_thread
    foreign key (session_id) references conversation_thread(session_id)
    on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create index idx_conversation_message_session_id
  on conversation_message(session_id, id);

create index idx_conversation_message_session_turn
  on conversation_message(session_id, turn_no);
