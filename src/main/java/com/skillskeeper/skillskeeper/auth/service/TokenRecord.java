package com.skillskeeper.skillskeeper.auth.service;

import java.time.Instant;

record TokenRecord(String username, Instant issuedAt) {
}
