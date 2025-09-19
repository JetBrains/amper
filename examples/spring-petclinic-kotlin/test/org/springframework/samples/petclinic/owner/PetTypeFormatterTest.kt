package org.springframework.samples.petclinic.owner


import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.BDDMockito.given
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.text.ParseException
import java.util.*

/**
 * Test class for [PetTypeFormatter]
 *
 * @author Colin But
 */
@ExtendWith(SpringExtension::class)
class PetTypeFormatterTest {

    @Mock
    private lateinit var pets: PetRepository

    private lateinit var petTypeFormatter: PetTypeFormatter

    @BeforeEach
    fun setup() {
        petTypeFormatter = PetTypeFormatter(pets)
    }

    @Test
    fun testPrint() {
        val petType = PetType()
        petType.name = "Hamster"
        val petTypeName = this.petTypeFormatter.print(petType, Locale.ENGLISH)
        assertThat("Hamster").isEqualTo(petTypeName)
    }

    @Test
    @Throws(ParseException::class)
    fun shouldParse() {
        given(this.pets.findPetTypes()).willReturn(makePetTypes())
        val petType = petTypeFormatter.parse("Bird", Locale.ENGLISH)
        assertThat("Bird").isEqualTo(petType.name)
    }

    @Test
    @Throws(ParseException::class)
    fun shouldThrowParseException() {
        given(this.pets.findPetTypes()).willReturn(makePetTypes())
        assertThrows(ParseException::class.java, { petTypeFormatter.parse("Fish", Locale.ENGLISH) })
    }

    /**
     * Helper method to produce some sample owner types just for test purpose
     *
     * @return [List] of [PetType]
     */
    private fun makePetTypes(): List<PetType> {
        val petTypes = ArrayList<PetType>()
        petTypes.add(object : PetType() {
            init {
                name = "Dog"
            }
        })
        petTypes.add(object : PetType() {
            init {
                name = "Bird"
            }
        })
        return petTypes
    }

}
