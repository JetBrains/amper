/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.github.singleton11.myapplication

import com.github.dkharrat.nexusdialog.FormActivity
import com.github.dkharrat.nexusdialog.FormController
import com.github.dkharrat.nexusdialog.controllers.EditTextController
import com.github.dkharrat.nexusdialog.controllers.FormSectionController

class MainActivity : FormActivity() {
    override fun initForm(p0: FormController?) {
        title = "Sign In"
        val section = FormSectionController(this, "Sign In")
        val loginController = EditTextController(this, "username", "Username")
        val passwordController = EditTextController(this, "password", "Password")
        passwordController.isSecureEntry = true
        section.addElement(loginController)
        section.addElement(passwordController)

        formController.addSection(section)
    }
}