Documention
For long-term use, please run your own server with the code and installation instructions on GitHub.

Lists of Books
Lists of book information in the database are queried using the API at /books (e.g. gutendex.com/books). Book data will be returned in the format

{
  "count": <number>,
  "next": <string or null>,
  "previous": <string or null>,
  "results": <array of Books>
}
where results is an array of 0-32 book objects, next and previous are URLs to the next and previous pages of results, and count in the total number of books for the query on all pages combined.

By default, books are ordered by popularity, determined by their numbers of downloads from Project Gutenberg.

Parameters can also be added to book-list queries in a typical URL format. For example, to get the first page of written by authors alive after 1899 and published in English or French, you can go to /books?author_year_start=1900&languages=en,fr

You can find available query parameters below.

author_year_start and author_year_end
Use these to find books with at least one author alive in a given range of years. They must have positive or negative integer values. For example, /books?author_year_end=-499 gives books with authors alive before 500 BCE, and /books?author_year_start=1800&author_year_end=1899 gives books with authors alive in the 19th Century.

copyright
Use this to find books with a certain copyright status: true for books with existing copyrights, false for books in the public domain in the USA, or null for books with no available copyright information. These can be combined with commas. For example, /books?copyright=true,false gives books with available copyright information.

ids
Use this to list books with Project Gutenberg ID numbers in a given list of numbers. They must be comma-separated positive integers. For example, /books?ids=11,12,13 gives books with ID numbers 11, 12, and 13.

languages
Use this to find books in any of a list of languages. They must be comma-separated, two-character language codes. For example, /books?languages=en gives books in English, and /books?languages=fr,fi gives books in either French or Finnish or both.

mime_type
Use this to find books with a given MIME type. Gutendex gives every book with a MIME type starting with the value. For example, /books?mime_type=text%2F gives books with types text/html, text/plain; charset=us-ascii, etc.; and /books?mime_type=text%2Fhtml gives books with types text/html, text/html; charset=utf-8, etc.

search
Use this to search author names and book titles with given words. They must be separated by a space (i.e. %20 in URL-encoded format) and are case-insensitive. For example, /books?search=dickens%20great includes Great Expectations by Charles Dickens.

sort
Use this to sort books: ascending for Project Gutenberg ID numbers from lowest to highest, descending for IDs highest to lowest, or popular (the default) for most popular to least popular by number of downloads.

topic
Use this to search for a case-insensitive key-phrase in books' bookshelves or subjects. For example, /books?topic=children gives books on the "Children's Literature" bookshelf, with the subject "Sick children -- Fiction", and so on.

Individual Books
Individual books can be found at /books/<id>, where <id> is the book's Project Gutenberg ID number. Error responses will appear in this format:

{
  "detail": <string of error message>
}
API Objects
Types of JSON objects served by Gutendex are given below.

Book
{
  "id": <number of Project Gutenberg ID>,
  "title": <string>,
  "subjects": <array of strings>,
  "authors": <array of Persons>,
  "summaries": <array of strings>,
  "translators": <array of Persons>,
  "bookshelves": <array of strings>,
  "languages": <array of strings>,
  "copyright": <boolean or null>,
  "media_type": <string>,
  "formats": <Format>,
  "download_count": <number>
}
Format
{
  <string of MIME-type>: <string of URL>,
  ...
}
Person
{
  "birth_year": <number or null>,
  "death_year": <number or null>,
  "name": <string>
}