/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading


//class AmperLangTreeReader(
//    val fileToRead: PsiFile,
//) : AmperElementVisitor() {
//    private val ctx = VisitorContext()
//
//    fun read(): TreeValue {
//        visitElement(fileToRead)
//        assert(ctx.nodesStack.size == 1) { "Stack is corrupted: ${ctx.nodesStack.size}" }
//        return ctx.nodesStack.first().build()
//    }
//
//    override fun visitContextBlock(o: AmperContextBlock) =
//        ctx.contextStack.pushAndPop(o.contextNameList.contextNames) { super.visitContextBlock(o) }
//
//    override fun visitContextualStatement(o: AmperContextualStatement) =
//        ctx.contextStack.pushAndPop(o.contextNameList.contextNames) { super.visitContextualStatement(o) }
//
//    override fun visitInvocationElement(o: AmperInvocationElement) =
//        ctx.addNewNode(StringKey(o.text), o) { super.visitInvocationElement(o) }
//
//    override fun visitProperty(o: AmperProperty) =
//        ctx.addNewNode(StringKey(o.name!!), o.nameElement!!) { super.visitProperty(o) }
//
//    override fun visitLiteral(o: AmperLiteral) =
//        ctx.addNewNode(StringKey(o.text!!), o)
//
//    override fun visitReferenceExpression(o: AmperReferenceExpression) =
//        ctx.addNewNode(StringKey(o.text!!), o)
//
//    override fun visitElement(o: PsiElement) {
//        ProgressIndicatorProvider.checkCanceled()
//        o.acceptChildren(this)
//    }
//}