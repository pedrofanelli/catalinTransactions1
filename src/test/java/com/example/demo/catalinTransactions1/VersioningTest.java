package com.example.demo.catalinTransactions1;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

public class VersioningTest {

	static EntityManagerFactory emf =
            Persistence.createEntityManagerFactory("Chapter11");
}
