package org.springframework.samples.petclinic

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.samples.petclinic.vet.VetRepository
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@SpringBootTest
class PetclinicIntegrationTests(@Autowired private val vets: VetRepository) {

    @Test
    fun testFindAll() {
        vets.findAll()
        vets.findAll() // served from cache
    }
}
