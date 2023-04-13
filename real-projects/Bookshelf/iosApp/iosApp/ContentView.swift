/*
 * Copyright 2021 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import SwiftUI
import shared

enum BookshelfError: Error {
    case bookNotFoundError(String)
}

class BookshelfViewModel : ObservableObject {
    
    private let repository = BookshelfRepository()
    private var job: Closeable? = nil
    
    @Published var searchResults = [ApiBook]()
    @Published var searching: Bool = false
    @Published var savedBooks = [Book]()
    
    func findBooks(title: String) {
        self.searching = true
        repository.getBookByTitle(title: title, completionHandler:
                                    { result, error in
                                        if let errorBooks = error {
                                            print(errorBooks.localizedDescription)
                                            self.searching = false
                                        }
                                        if let resultBooks: [ApiBook] = result {
                                            self.searchResults.removeAll()
                                            self.searchResults = resultBooks
                                            self.searching = false
                                        }
                                    })
    }
    
    func addBook(book: Book) {
        self.repository.addToBookshelf(book: book)
    }
    
    func removeBook(bookId: String) {
        self.repository.removeFromBookshelf(title: bookId)
    }
    
    func getUnsavedBook(bookId: String) throws -> Book {
        let book = self.searchResults.first { (apiBook: ApiBook) -> Bool in
            apiBook.title == bookId
        }?.toRealmBook()
        if (book == nil) {
            throw BookshelfError.bookNotFoundError("Book \(bookId) not found.")
        }
        return book!
    }
    
    func startObservingSavedBooks() {
        self.job = self.repository.allBooksAsCommonFlowable().watch { books in
            self.savedBooks = books as! [Book]
        }
    }
    
    func stopObservingSavedBooks() {
        job?.close()
    }
}

struct ContentView: View {
    @State private var selection = 0
    @State private var searchByTitle = ""
    @State private var searchText = ""
    @StateObject var viewModel = BookshelfViewModel()
    
    var body: some View {
        NavigationView {
            TabView(selection: $selection) {
                SearchScreen(
                    selection: $selection,
                    searchText: $searchText,
                    viewModel: viewModel
                ).tabItem {
                    Image(systemName: "house.fill")
                    Text("Home")
                }.tag(0)
                
                MySavedBooks(
                    selection: $selection,
                    viewModel: viewModel
                ).tabItem {
                    Image(systemName: "bookmark.circle.fill")
                    Text("Books")
                }.tag(1)
                
                AboutScreen()
                    .font(.system(size: 30, weight: .bold, design: .rounded))
                    .tabItem {
                        Image(systemName: "video.circle.fill")
                        Text("About")
                    }.tag(2)
                
            }.onAppear() {
                UITabBar.appearance().barTintColor = .white
            }.navigationTitle("Bookshelf")
        }
    }
}

struct SearchScreen: View {
    @Binding var selection: Int
    @Binding var searchText: String
    @ObservedObject var viewModel: BookshelfViewModel
    
    var body: some View {
        VStack {
            SearchBar(text: $searchText, viewModel: viewModel)
            if (viewModel.searching) {
                ProgressView("🔎 Openlibrary.org…")
                    .frame(minWidth: 0, maxWidth: .infinity, minHeight: 0, maxHeight: .infinity)
            } else {
                List(viewModel.searchResults, id: \.self) { book in
                    NavigationLink(
                        destination: DetalisView(
                            book: book.toRealmBook(),
                            selection: $selection,
                            viewModel: viewModel
                        ),
                        label: {
                            Text(book.title)
                                .font(.system(size: 20, weight: .bold, design: .rounded))
                        }
                    )
                }
            }
        }
    }
}

struct SearchBar: View {
    @Binding var text: String
    @State private var isEditing = false
    @ObservedObject var viewModel: BookshelfViewModel
    
    var body: some View {
        HStack {
            TextField("Search ...", text: $text)
                .padding(7)
                .padding(.horizontal, 25)
                .background(Color(.systemGray6))
                .cornerRadius(8)
                .overlay(
                    HStack {
                        Image(systemName: "magnifyingglass")
                            .foregroundColor(.gray)
                            .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
                            .padding(.leading, 8)
                        
                        if isEditing {
                            Button(action: {
                                self.text = ""
                            }) {
                                Image(systemName: "multiply.circle.fill")
                                    .foregroundColor(.gray)
                                    .padding(.trailing, 8)
                            }
                        }
                    }
                )
                .padding(.horizontal, 10)
                .onTapGesture {
                    self.isEditing = true
                }
            
            if isEditing {
                Button(action: {
                    self.isEditing = false
                    viewModel.findBooks(title: self.text)
                    
                    self.text = ""
                    // Dismiss the keyboard
                    UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                }) {
                    Text("Find")
                }.disabled(self.text.isEmpty)
                .padding(.trailing, 10)
                .transition(.move(edge: .trailing))
                .animation(.default)
            }
        }
    }
}

struct MySavedBooks: View {
    @Binding var selection: Int
    @ObservedObject var viewModel: BookshelfViewModel
    
    var body: some View {
        VStack {
            if (viewModel.savedBooks.isEmpty) {
                Text("Your bookshelf is empty")
            } else {
                List(viewModel.savedBooks, id: \.self) { book in
                    NavigationLink(
                        destination: DetalisView(
                            book: book,
                            selection: $selection,
                            viewModel: viewModel
                        ),
                        label: {
                            Text(book.title)
                                .font(.system(size: 20, weight: .bold, design: .rounded))
                        }
                    )
                }
            }
        }.onAppear {
            viewModel.startObservingSavedBooks()
        }.onDisappear {
            viewModel.stopObservingSavedBooks()
        }
    }
}

enum DetailsMode {
    case add
    case remove
}

struct DetalisView: View {
    var book: Book
    @Binding var selection: Int
    @ObservedObject var viewModel: BookshelfViewModel
    @Environment(\.presentationMode) var presentationMode: Binding<PresentationMode>

    var body: some View {
        let cachedBook = viewModel.savedBooks.first { (realmBook: Book) -> Bool in
            realmBook.title == book.title
        }
        let screenMode = cachedBook == nil ? DetailsMode.add : DetailsMode.remove

        HStack {
            Text(book.title)
            Button(
                action: {
                    if (screenMode == DetailsMode.add) {
                        viewModel.addBook(book: book)
                    } else {
                        viewModel.removeBook(bookId: book.title)
                    }
                    selection = 1 // Navigate to Books
                    presentationMode.wrappedValue.dismiss() // Pop view
                },
                label: {
                    Text(screenMode == DetailsMode.add ? "Add" : "Remove")
                }
            )
        }
    }
}

struct AboutScreen: View {
    @Environment(\.openURL) var openURL
    
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 25, style: .continuous)
                .fill(Color.gray)
            Button(action: {
                    openURL(URL(string: "https://www.github.com/realm/realm-kotlin")!)}, label: {
                        Text("""
Demo app using Realm-Kotlin Multiplatform SDK

🎨 UI: using SwiftUI
---- Shared ---
📡 Network: using Ktor and Kotlinx.serialization
💾 Persistence: using Realm Database
""")
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .font(.body)
                            .foregroundColor(.black)
                    }
            )
        }
        .padding(20)
        .multilineTextAlignment(.center)
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
