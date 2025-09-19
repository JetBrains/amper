package org.springframework.samples.petclinic.owner


import org.assertj.core.util.Lists
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.samples.petclinic.visit.VisitRepository
import org.springframework.samples.petclinic.visit.Visit
import java.time.LocalDate
import java.util.*


/**
 * Test class for [OwnerController]
 *
 * @author Colin But
 */
@ExtendWith(SpringExtension::class)
@WebMvcTest(OwnerController::class)
class OwnerControllerTest {

    @Autowired
    lateinit private var mockMvc: MockMvc

    @MockitoBean
    private lateinit var owners: OwnerRepository

    @MockitoBean
    private lateinit var visits: VisitRepository

    private lateinit var george: Owner

    @BeforeEach
    fun setup() {
        george = Owner()
        george.id = TEST_OWNER_ID
        george.firstName = "George"
        george.lastName = "Franklin"
        george.address = "110 W. Liberty St."
        george.city = "Madison"
        george.telephone = "6085551023"
        val max = Pet()
        val dog = PetType()
        dog.name = "dog"
        max.id = 1
        max.type = dog
        max.name = "Max"
        max.birthDate = LocalDate.now()
        george.pets = mutableSetOf(max)
        given(owners.findById(TEST_OWNER_ID)).willReturn(george)
        val visit = Visit()
        visit.date = LocalDate.now()
        given(this.visits.findByPetId(max.id!!)).willReturn(Collections.singleton(visit))
    }

    @Test
    fun testInitCreationForm() {
        mockMvc.perform(get("/owners/new"))
                .andExpect(status().isOk)
                .andExpect(model().attributeExists("owner"))
                .andExpect(view().name("owners/createOrUpdateOwnerForm"))
    }

    @Test
    fun testProcessCreationFormSuccess() {
        mockMvc.perform(post("/owners/new")
                .param("firstName", "Joe")
                .param("lastName", "Bloggs")
                .param("address", "123 Caramel Street")
                .param("city", "London")
                .param("telephone", "01316761638")
        )
                .andExpect(status().is3xxRedirection)
    }

    @Test
    fun testProcessCreationFormHasErrors() {
        mockMvc.perform(post("/owners/new")
                .param("firstName", "Joe")
                .param("lastName", "Bloggs")
                .param("city", "London")
        )
                .andExpect(status().isOk)
                .andExpect(model().attributeHasErrors("owner"))
                .andExpect(model().attributeHasFieldErrors("owner", "address"))
                .andExpect(model().attributeHasFieldErrors("owner", "telephone"))
                .andExpect(view().name("owners/createOrUpdateOwnerForm"))
    }

    @Test
    fun testInitFindForm() {
        mockMvc.perform(get("/owners/find"))
                .andExpect(status().isOk)
                .andExpect(model().attributeExists("owner"))
                .andExpect(view().name("owners/findOwners"))
    }

    @Test
    fun testProcessFindFormSuccess() {
        given(owners.findByLastName("")).willReturn(Lists.newArrayList<Owner>(george, Owner()))
        mockMvc.perform(get("/owners"))
                .andExpect(status().isOk)
                .andExpect(view().name("owners/ownersList"))
    }

    @Test
    fun testProcessFindFormByLastName() {
        given(owners.findByLastName(george.lastName)).willReturn(Lists.newArrayList<Owner>(george))
        mockMvc.perform(get("/owners")
                .param("lastName", "Franklin")
        )
                .andExpect(status().is3xxRedirection)
                .andExpect(view().name("redirect:/owners/" + TEST_OWNER_ID))
    }

    @Test
    fun testProcessFindFormNoOwnersFound() {
        mockMvc.perform(get("/owners")
                .param("lastName", "Unknown Surname")
        )
                .andExpect(status().isOk)
                .andExpect(model().attributeHasFieldErrors("owner", "lastName"))
                .andExpect(model().attributeHasFieldErrorCode("owner", "lastName", "notFound"))
                .andExpect(view().name("owners/findOwners"))
    }

    @Test
    fun testInitUpdateOwnerForm() {
        mockMvc.perform(get("/owners/{ownerId}/edit", TEST_OWNER_ID))
                .andExpect(status().isOk)
                .andExpect(model().attributeExists("owner"))
                .andExpect(model().attribute("owner", hasProperty<Any>("lastName", `is`("Franklin"))))
                .andExpect(model().attribute("owner", hasProperty<Any>("firstName", `is`("George"))))
                .andExpect(model().attribute("owner", hasProperty<Any>("address", `is`("110 W. Liberty St."))))
                .andExpect(model().attribute("owner", hasProperty<Any>("city", `is`("Madison"))))
                .andExpect(model().attribute("owner", hasProperty<Any>("telephone", `is`("6085551023"))))
                .andExpect(view().name("owners/createOrUpdateOwnerForm"))
    }

    @Test
    fun testProcessUpdateOwnerFormSuccess() {
        mockMvc.perform(post("/owners/{ownerId}/edit", TEST_OWNER_ID)
                .param("firstName", "Joe")
                .param("lastName", "Bloggs")
                .param("address", "123 Caramel Street")
                .param("city", "London")
                .param("telephone", "01616291589")
        )
                .andExpect(status().is3xxRedirection)
                .andExpect(view().name("redirect:/owners/{ownerId}"))
    }

    @Test
    fun testProcessUpdateOwnerFormHasErrors() {
        mockMvc.perform(post("/owners/{ownerId}/edit", TEST_OWNER_ID)
                .param("firstName", "Joe")
                .param("lastName", "Bloggs")
                .param("city", "London")
        )
                .andExpect(status().isOk)
                .andExpect(model().attributeHasErrors("owner"))
                .andExpect(model().attributeHasFieldErrors("owner", "address"))
                .andExpect(model().attributeHasFieldErrors("owner", "telephone"))
                .andExpect(view().name("owners/createOrUpdateOwnerForm"))
    }

    @Test
    fun testShowOwner() {
        mockMvc.perform(get("/owners/{ownerId}", TEST_OWNER_ID))
                .andExpect(status().isOk)
                .andExpect(model().attribute("owner", hasProperty<Any>("lastName", `is`("Franklin"))))
                .andExpect(model().attribute("owner", hasProperty<Any>("firstName", `is`("George"))))
                .andExpect(model().attribute("owner", hasProperty<Any>("address", `is`("110 W. Liberty St."))))
                .andExpect(model().attribute("owner", hasProperty<Any>("city", `is`("Madison"))))
                .andExpect(model().attribute("owner", hasProperty<Any>("telephone", `is`("6085551023"))))
                .andExpect(model().attribute("owner", hasProperty<Any>("pets", not<Any>(empty<Any>()))))
                .andExpect(view().name("owners/ownerDetails"))

        Mockito.verify(this.visits).findByPetId(1)
    }


    companion object {

        private const val TEST_OWNER_ID = 1
    }

}
