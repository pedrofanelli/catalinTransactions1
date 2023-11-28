package com.example.demo.catalinTransactions1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

import com.example.demo.catalinTransactions1.models.Item;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.Persistence;



public class VersioningTest {

	
	public static EntityManagerFactory emf =
            Persistence.createEntityManagerFactory("holahola");
	
	/**
	 * 
	 * 
	 * 
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	@Test
    public void firstCommitWins() throws ExecutionException, InterruptedException {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        Item someItem = new Item("Some Item");
        em.persist(someItem);
        em.getTransaction().commit();
        em.close();
        final Long ITEM_ID = someItem.getId();

        EntityManager em1 = emf.createEntityManager();
        em1.getTransaction().begin();

        /*
           Retrieving an entity instance by identifier loads the current version from the
           database with a <code>SELECT</code>.
        */
        Item item = em1.find(Item.class, ITEM_ID);
        // select * from ITEM where ID = ?

        /*
           The current version of the <code>Item</code> instance is 0.
        */
        assertEquals(0, item.getVersion());

        item.setName("New Name");

        // The concurrent second unit of work doing the same
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                EntityManager em2 = emf.createEntityManager();
                em2.getTransaction().begin();

                Item item1 = em2.find(Item.class, ITEM_ID);
                // select * from ITEM where ID = ?

                assertEquals(0, item1.getVersion());

                item1.setName("Other Name");

                em2.getTransaction().commit();
                // update ITEM set NAME = ?, VERSION = 1 where ID = ? and VERSION = 0
                // This succeeds, there is a row with ID = ? and VERSION = 0 in the database!
                em2.close();

            } catch (Exception ex) {
                // This shouldn't happen, this commit should win!
                throw new RuntimeException("Concurrent operation failure: " + ex, ex);
            }
            return null;
        }).get();

        /*
           When the persistence context is flushed Hibernate will detect the dirty
           <code>Item</code> instance and increment its version to 1. The SQL
           <code>UPDATE</code> now performs the version check, storing the new version
           in the database, but only if the database version is still 0.
        */
        assertThrows(OptimisticLockException.class, () -> em1.flush());
        // update ITEM set NAME = ?, VERSION = 1 where ID = ? and VERSION = 0

    }
}
