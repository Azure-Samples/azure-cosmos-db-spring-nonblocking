package com.example.test_t;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Primary;
import org.springframework.util.Assert;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.CountDownLatch;

@SpringBootApplication
public class TestTApplication implements CommandLineRunner {
	private static final Logger LOGGER = LoggerFactory.getLogger(TestTApplication.class);

	@Autowired
	private UserRepository repository;

	public static void main(String[] args) {
		SpringApplication.run(TestTApplication.class, args);
	}

	public void run(String... var1) throws Exception {

		final User testUser = new User("12", "Tasha1", "Calderon1", "4567 Main St Buffalo, NY 98052");

		LOGGER.info("Saving user: {}", testUser);

		// Save the User class to Azure CosmosDB database.
		final Mono<User> saveUserMono = repository.save(testUser);

		final Flux<User> firstNameUserFlux = repository.findByFirstName("testFirstName");

		// Nothing happens until we subscribe to these Monos.

		CountDownLatch latch = new CountDownLatch(1);

		// Running one statement and await//
		repository.findById(testUser.getId()).subscribeOn(Schedulers.elastic()).subscribe(result -> {
			Assert.isNull(result, "User must be null");
			System.out.println("Im in id");
		}, Throwable::printStackTrace, latch::countDown);

		latch.await();

		// ---------------------//
		latch = new CountDownLatch(1);
		// Running single statement and then await//
		saveUserMono.subscribeOn(Schedulers.elastic()).subscribe(savedUser -> {
			Assert.state(savedUser != null, "Saved user must not be null");
			Assert.state(savedUser.getFirstName().equals(testUser.getFirstName()),
					"Saved user first name doesn't match");
			LOGGER.info("Saved user");
			System.out.println(savedUser.getFirstName());
		}, Throwable::printStackTrace, latch::countDown);

		latch.await();
		// -----------------//

		// Running multiple statements and then await//
		firstNameUserFlux.collectList().subscribeOn(Schedulers.elastic()).subscribe(x -> {
			x.forEach(obj -> {
				System.out.println(obj.getFirstName());
			});
		}, Throwable::printStackTrace, latch::countDown);

		repository.findById(testUser.getId()).subscribeOn(Schedulers.elastic()).subscribe(result -> {
			LOGGER.info("Found user by findById : {}", result);
			Assert.state(result.getFirstName().equals(testUser.getFirstName()),
					"query result firstName doesn't match!");
			Assert.state(result.getLastName().equals(testUser.getLastName()), "query result lastName doesn't match!");
		}, Throwable::printStackTrace, latch::countDown);
		// -------------//
		latch.await();
	}

	@PostConstruct
	public void setup() throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(1);
		
		LOGGER.info("Clear the database");
		
		this.repository.deleteAll().subscribeOn(Schedulers.elastic()).subscribe(x -> {
		}, Throwable::printStackTrace, latch::countDown);
		
		latch.await();
	}

	@PreDestroy
	public void cleanup() throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(1);
		
		LOGGER.info("Cleaning up users");
		//cleanup with unblocking way.
		this.repository.deleteAll().subscribeOn(Schedulers.elastic()).subscribe(x -> {
		}, Throwable::printStackTrace, latch::countDown);
		
		latch.await();
	}
}
