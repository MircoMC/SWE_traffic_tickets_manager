-- Demo data. Fines follow the same rules as the code: amount = 150 EUR + 10 EUR for every km/h over the limit,
-- due date = 60 days after the detection. Password hashes were generated with PBKDF2WithHmacSHA256
-- (600000 iterations), the same algorithm as PasswordHasher. Demo passwords: see db/README.md.

INSERT INTO users (id, tax_code, full_name, password_salt, password_hash, role, badge_number) VALUES
  (1, 'RSSMRA80A01H501U', 'Mario Rossi', decode('5a72f7ec1740fa3583a9abbf4f6929bc','hex'), decode('9fab15499809affa0e15fed9c4dd17d309718e07ac39de6bb1f2735d71581817','hex'), 'CITIZEN', NULL),
  (2, 'LBRGLA85C15F205W', 'Gianluca Bianchi', decode('345c2ab2fe25c93584adda09d13f4c58','hex'), decode('3348da91f1810bbc713f092dd45cbd45b81401cbef970d463f691d7bd71f8313','hex'), 'CITIZEN', NULL),
  (3, 'MNCFLV92D20H501X', 'Flavia Mancini', decode('81e0267c750b95154d88acf03ae7f863','hex'), decode('bb5c723640a16bb6976a9683b333a80d4391aa14acb8613aa16a9845b306c811','hex'), 'CITIZEN', NULL),
  (4, 'VRDVNT78E10L219Y', 'Vincenzo Verde', decode('81997f39de5d85b464f9a895ba6ed0e7','hex'), decode('cb9fbad59301dc5ea49fbced050d303f7206e58f37df399bf734d5eae6813885','hex'), 'CITIZEN', NULL),
  (5, 'GNNVLD90B02F205Z', 'Valerio Gialli', decode('dc42fd7d1c034e7bc2f7d702a60930e8','hex'), decode('18df0f36b0af4c68f1f2720f9eb858e9f428bdfb59a4740b8e8c6d738976a775','hex'), 'POLICE_OFFICER', 'POL-4581'),
  (6, 'SNTLRA88P45F205K', 'Laura Santoro', decode('967e92c78e9a9583ffc4ec1fd0e6c9cd','hex'), decode('78caf286fe443a93fb109ce04a81a0021de72315ef1fee4e92c1f09f1c500df9','hex'), 'POLICE_OFFICER', 'POL-7720'),
  (7, 'MRTGPP65M10H501Q', 'Giuseppe Martini', decode('fbafb4007b2906e17112a6edb0331675','hex'), decode('d19211aa8e5beb8097b31302de268f9d6f62d6be700d169afabd476b82f9da33','hex'), 'JUDGE', NULL),
  (8, 'NREANN75C41F205T', 'Anna Neri', decode('1ec594b89e81b67063a29a20ce383d82','hex'), decode('26df5627a3f40a25ea66c30899eeb6b85105f81d2525435b30438742485579ba','hex'), 'ADMINISTRATOR', NULL);

INSERT INTO speed_cameras (id, address, latitude, longitude, speed_limit) VALUES
  ('AVX-001', 'Via Roma 1, Roma',            41.902783, 12.496366, 50),
  ('AVX-002', 'Corso Buenos Aires 10, Milano', 45.464204,  9.190000, 70),
  ('AVX-003', 'Via dei Fori Imperiali, Roma',  41.890210, 12.492231, 90),
  ('AVX-004', 'Via Dante 5, Milano',          45.464270,  9.189510, 30);

INSERT INTO plate_ownerships (plate, owner_id, since) VALUES
  ('AB123CD', 1, '2024-01-01 00:00:00+00'),
  ('EF456GH', 2, '2024-01-01 00:00:00+00'),
  ('GH789IJ', 3, '2025-03-01 00:00:00+00'),
  ('KL012MN', 4, '2023-06-01 00:00:00+00'),
  ('XY999ZZ', 1, '2024-01-01 00:00:00+00'),
  ('XY999ZZ', 3, '2026-09-15 00:00:00+00');

INSERT INTO fines (id, owner_id, amount_cents, due_date, state, camera_id, plate, detected_speed, speed_limit,
                   detected_at, judge_id, ruling_reason, ruling_date) VALUES
  ('550e8400-e29b-41d4-a716-446655440000', 1, 33000, '2026-12-25', 'ISSUED',    'AVX-001', 'AB123CD',  68, 50, '2026-10-26 10:30:00+00', NULL, NULL, NULL),
  ('550e8400-e29b-41d4-a716-446655440001', 2, 30000, '2026-10-31', 'PAID',      'AVX-002', 'EF456GH',  85, 70, '2026-09-01 09:15:00+00', NULL, NULL, NULL),
  ('550e8400-e29b-41d4-a716-446655440002', 2, 50000, '2026-12-04', 'ISSUED',    'AVX-003', 'EF456GH', 125, 90, '2026-10-05 11:00:00+00', NULL, NULL, NULL),
  ('550e8400-e29b-41d4-a716-446655440003', 3, 33000, '2026-12-11', 'PAID',      'AVX-004', 'GH789IJ',  48, 30, '2026-10-12 16:40:00+00', NULL, NULL, NULL),
  ('550e8400-e29b-41d4-a716-446655440004', 4, 37000, '2026-07-09', 'EXPIRED',   'AVX-001', 'KL012MN',  72, 50, '2026-05-10 08:30:00+00', NULL, NULL, NULL),
  ('550e8400-e29b-41d4-a716-446655440005', 4, 67000, '2026-07-31', 'CANCELLED', 'AVX-003', 'KL012MN', 142, 90, '2026-06-01 10:00:00+00',
        7, 'Detection error: camera calibration expired', '2026-07-20');

INSERT INTO fine_events (fine_id, event, occurred_at, author_id) VALUES
  ('550e8400-e29b-41d4-a716-446655440000', 'ISSUED',    '2026-10-26 10:30:00+00', NULL),
  ('550e8400-e29b-41d4-a716-446655440001', 'ISSUED',    '2026-09-01 09:15:00+00', NULL),
  ('550e8400-e29b-41d4-a716-446655440001', 'PAID',      '2026-09-10 14:22:00+00', NULL),
  ('550e8400-e29b-41d4-a716-446655440002', 'ISSUED',    '2026-10-05 11:00:00+00', NULL),
  ('550e8400-e29b-41d4-a716-446655440003', 'ISSUED',    '2026-10-12 16:40:00+00', NULL),
  ('550e8400-e29b-41d4-a716-446655440003', 'PAID',      '2026-10-15 18:05:00+00', NULL),
  ('550e8400-e29b-41d4-a716-446655440004', 'ISSUED',    '2026-05-10 08:30:00+00', NULL),
  ('550e8400-e29b-41d4-a716-446655440004', 'EXPIRED',   '2026-07-10 00:00:00+00', NULL),
  ('550e8400-e29b-41d4-a716-446655440005', 'ISSUED',    '2026-06-01 10:00:00+00', NULL),
  ('550e8400-e29b-41d4-a716-446655440005', 'CANCELLED', '2026-07-20 15:30:00+00', 7);
