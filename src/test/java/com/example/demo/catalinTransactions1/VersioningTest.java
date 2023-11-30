package com.example.demo.catalinTransactions1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

import com.example.demo.catalinTransactions1.models.Bid;
import com.example.demo.catalinTransactions1.models.Category;
import com.example.demo.catalinTransactions1.models.Item;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
import jakarta.persistence.NoResultException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.Persistence;
import jakarta.persistence.RollbackException;



public class VersioningTest {

	
	public static EntityManagerFactory emf =
            Persistence.createEntityManagerFactory("holahola");
	
	/**
	 * Optimistic Concurrency Control
	 * Funcionamiento de un "Optimistic Lock" es decir, como Hibernate puede hacer un versioning automático
	 * entonces permite tener un control muy similar a un Repeatable Read (RR) Isolation. Pero SIN lockear la 
	 * base de datos. Por default existe un Read Committed pero con este versioning se asimila al Repeatable Read.
	 * Por eso se usa bastante. Mantenemos un nivel de isolation Read Committed, con toda la mejor performance que
	 * eso implica pero además gracias al versioning propio de Hibernate tenemos un nivel de isolation como RR.
	 * 
	 * 
	 * Cada vez que la instancia o la entidad este "DIRTY" al momento de Flushear, se incrementará la versión.
	 * Es decir, que la entidad original que se obtuvo y la que se guarda sean diferentes. Si el cambio lo hicimos
	 * nosotros, entonces la versión será la misma que la que tenia al leerla o tomarla. Por ejemplo, supongamos que 
	 * buscamos una entidad, la obtenemos y tiene version 0. La cambiamos, y al momento de guardarla, se hace un 
	 * dirty check, porque es diferente a la original en la base. La va a guardar si la versión es la misma que la 
	 * del comienzo (0 en el caso). Le mete el cambio y aumenta en 1 la versión.
	 * PERO, si la versión es distinta, ahí se tira @OptimisticLockException y podremos manejarlo.
	 * Por eso es optimista, porque funciona como si hubiera habido un lock en la base, pero no existio, y nos
	 * permite controlar esa situación. La forma pesimista sería que se setee un lock en la row, y nadie puede tocarla
	 * mientras tanto. En nuestro caso alguien puede tocarla, y en cuyo caso podemos controlarlo.
	 * 
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	@Test
    public void firstCommitWins() throws ExecutionException, InterruptedException {
		System.out.println("buenas buenas");
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
           in the database, but ONLY if the database version is still 0.
        */
        assertThrows(OptimisticLockException.class, () -> em1.flush());
        // update ITEM set NAME = ?, VERSION = 1 where ID = ? and VERSION = 0

    }
	
	ConcurrencyTestData storeCategoriesAndItems() {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();

        ConcurrencyTestData testData = new ConcurrencyTestData();
        testData.categories = new TestData(new Long[3]);
        testData.items = new TestData(new Long[5]);
        for (int i = 1; i <= testData.categories.identifiers.length; i++) {
            Category category = new Category();
            category.setName("Category: " + i);
            em.persist(category);
            testData.categories.identifiers[i - 1] = category.getId();
            for (int j = 1; j <= testData.categories.identifiers.length; j++) {
                Item item = new Item("Item " + j);
                item.setCategory(category);
                item.setBuyNowPrice(new BigDecimal(10 + j));
                em.persist(item);
                testData.items.identifiers[(i - 1) + (j - 1)] = item.getId();
            }
        }
        em.getTransaction().commit();
        em.close();
        return testData;
    }
	
	/**
	 * 
	 * En este caso obligamos que antes de realizarse un flush o commit, se haga un select sobre los
	 * Item entities. Si prestamos atención, no realizamos ningún cambio sobre ellos, simplemente
	 * calculamos la suma de los precios de los items dentro de una categoría específica.
	 * Concurrentemente se puede cambiar la categoria de cierto item, pero como nosotros no lo 
	 * cambiamos, no ejecutamos ningun UPDATE entonces el optimistic lock automático no nos funcionará.
	 * 
	 * Entonces, obtendríamos una suma incorrecta. Para asegurarnos que nadie movio nada de lugar o cambio algo,
	 * obligamos que antes de commit, se chekee la versión.
	 * 
	 * PERO, estoy casi seguro que no aumentaría la versión, sino simplemente la controlaría, un SELECT
	 * 
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	@Test
    void manualVersionChecking() throws ExecutionException, InterruptedException {
        final ConcurrencyTestData testData = storeCategoriesAndItems();
        Long[] CATEGORIES = testData.categories.identifiers;

        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();

        BigDecimal totalPrice = BigDecimal.ZERO;
        for (Long categoryId : CATEGORIES) {

                /*
                   For each <code>Category</code>, query all <code>Item</code> instances with
                   an <code>OPTIMISTIC</code> lock mode. Hibernate now knows it has to
                   check each <code>Item</code> at flush time.
                 */
            List<Item> items =
                    em.createQuery("select i from Item i where i.category.id = :catId", Item.class)
                    		.setLockMode(LockModeType.OPTIMISTIC)
                            .setParameter("catId", categoryId)
                            .getResultList();

            for (Item item : items)
                totalPrice = totalPrice.add(item.getBuyNowPrice());

            // Now a concurrent transaction will move an item to another category
            if (categoryId.equals(testData.categories.getFirstId())) {
                Executors.newSingleThreadExecutor().submit(() -> {
                    try {
                        EntityManager em1 = emf.createEntityManager();
                        em1.getTransaction().begin();

                        // Moving the first item from the first category into the last category
                        List<Item> items1 =
                                em1.createQuery("select i from Item i where i.category.id = :catId", Item.class)
                                        .setParameter("catId", testData.categories.getFirstId())
                                        .getResultList();

                        Category lastCategory = em1.getReference(
                                Category.class, testData.categories.getLastId()
                        );

                        items1.iterator().next().setCategory(lastCategory);

                        em1.getTransaction().commit();
                        em1.close();
                    } catch (Exception ex) {
                        // This shouldn't happen, this commit should win!
                        throw new RuntimeException("Concurrent operation failure: " + ex, ex);
                    }
                    return null;
                }).get();
            }
        }

            /*
               For each <code>Item</code> loaded earlier with the locking query, Hibernate will
               now execute a <code>SELECT</code> during flushing. It checks if the database
               version of each <code>ITEM</code> row is still the same as when it was loaded
               earlier. If any <code>ITEM</code> row has a different version, or the row doesn't
               exist anymore, an <code>OptimisticLockException</code> will be thrown.
               
               ... NOT exactly that exception :)
             */
        
        assertThrows(RollbackException.class, () -> em.getTransaction().commit());

        em.close();

        // NOT 108
        assertEquals("119.00", totalPrice.toString());
    }
	
	private TestData storeItemAndBids() {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        Long[] ids = new Long[1];
        Item item = new Item("Some Item");
        em.persist(item);
        ids[0] = item.getId();
        for (int i = 1; i <= 3; i++) {
            Bid bid = new Bid(new BigDecimal(10 + i), item);
            em.persist(bid);
        }
        em.getTransaction().commit();
        em.close();
        return new TestData(ids);
    }

    private Bid queryHighestBid(EntityManager em, Item item) {
        // Can't scroll with cursors in JPA, have to use setMaxResult()
        try {
            return (Bid) em.createQuery(
                    "select b from Bid b" +
                            " where b.item = :itm" +
                            " order by b.amount desc"
            )
                    .setParameter("itm", item)
                    .setMaxResults(1)
                    .getSingleResult();
        } catch (NoResultException ex) {
            return null;
        }
    }
	
	@Test
    void forceIncrement() throws Throwable {
        TestData testData = storeItemAndBids();
        Long ITEM_ID = testData.getFirstId();

        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();

        /*
           The <code>find()</code> method accepts a <code>LockModeType</code>. The
           <code>OPTIMISTIC_FORCE_INCREMENT</code> mode tells Hibernate that the version
           of the retrieved <code>Item</code> should be incremented after loading,
           even if it's never modified in the unit of work.
        */
        Item item = em.find(
                Item.class,
                ITEM_ID,
                LockModeType.OPTIMISTIC_FORCE_INCREMENT
        );

        Bid highestBid = queryHighestBid(em, item);

        // Now a concurrent transaction will place a bid for this item, and
        // succeed because the first commit wins!
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                EntityManager em1 = emf.createEntityManager();
                em1.getTransaction().begin();

                Item item1 = em1.find(
                        Item.class,
                        testData.getFirstId(),
                        LockModeType.OPTIMISTIC_FORCE_INCREMENT
                );
                Bid highestBid1 = queryHighestBid(em1, item1);

                Bid newBid = new Bid(
                        new BigDecimal("44.44"),
                        item1,
                        highestBid1
                );
                em1.persist(newBid);

                em1.getTransaction().commit();
                em1.close();
            } catch (Exception ex) {
                // This shouldn't happen, this commit should win!
                throw new RuntimeException("Concurrent operation failure: " + ex, ex);
            }
            return null;
        }).get();

        /*
           The code persists a new <code>Bid</code> instance; this does not affect
           any values of the <code>Item</code> instance. A new row will be inserted
           into the <code>BID</code> table. Hibernate would not detect concurrently
           made bids at all without a forced version increment of the
           <code>Item</code>. We also use a checked exception to validate the
           new bid amount; it must be greater than the currently highest bid.
        */
        Bid newBid = new Bid(
                new BigDecimal("45.45"),
                item,
                highestBid
        );
        em.persist(newBid);

        /*
            When flushing the persistence context, Hibernate will execute an
            <code>INSERT</code> for the new <code>Bid</code> and force an
            <code>UPDATE</code> of the <code>Item</code> with a version check.
            If someone modified the <code>Item</code> concurrently, or placed a
            <code>Bid</code> concurrently with this procedure, Hibernate throws
            an exception.
        */
        assertThrows(RollbackException.class, () -> em.getTransaction().commit());
        em.close();
    }
	
}
