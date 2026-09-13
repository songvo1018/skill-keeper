package com.skillskeeper.skillskeeper.auth;

import java.time.Instant;

record TokenRecord(String username, Instant issuedAt) {
}
