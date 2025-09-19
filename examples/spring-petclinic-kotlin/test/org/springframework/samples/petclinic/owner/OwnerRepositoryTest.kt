package org.springframework.samples.petclinic.owner

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.transaction.annotation.Transactional


@ExtendWith(SpringExtension::class)
@DataJpaTest
class OwnerRepositoryTest(@Autowired private val owners: OwnerRepository) {

    @Test
    fun shouldFindOwnersByLastName() {
        var owners = this.owners.findByLastName("Davis")
        assertThat(owners.size).isEqualTo(2)

        owners = this.owners.findByLastName("Daviss")
        assertThat(owners.isEmpty()).isTrue()
    }

    @Test
    fun shouldFindSingleOwnerWithPet() {
        val owner = this.owners.findById(1)
        assertThat(owner.lastName).startsWith("Franklin")
        assertThat(owner.pets.size).isEqualTo(1)
        assertThat(owner.getPets()[0].type).isNotNull()
        assertThat(owner.getPets()[0].type!!.name).isEqualTo("cat")
    }

    @Test
    @Transactional
    fun shouldInsertOwner() {
        var owners = this.owners.findByLastName("Schultz")
        val found = owners.size

        val owner = Owner()
        owner.firstName = "Sam"
        owner.lastName = "Schultz"
        owner.address = "4, Evans Street"
        owner.city = "Wollongong"
        owner.telephone = "4444444444"
        this.owners.save(owner)
        assertThat(owner.id?.toLong()).isNotEqualTo(0)

        owners = this.owners.findByLastName("Schultz")
        assertThat(owners.size).isEqualTo(found + 1)
    }

    @Test
    @Transactional
    fun shouldUpdateOwner() {
        var owner = this.owners.findById(1)
        val oldLastName = owner.lastName
        val newLastName = oldLastName + "X"

        owner.lastName = newLastName
        this.owners.save(owner)

        // retrieving new name from database
        owner = this.owners.findById(1)
        assertThat(owner.lastName).isEqualTo(newLastName)
    }
}
