package com.seeker.share;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:context-test;DB_CLOSE_DELAY=-1",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"seeker.share.storage-location=${java.io.tmpdir}/seeker-share-context-test"
})
class SeekerShareApplicationTests {

	@Test
	void contextLoads() {
	}

}
