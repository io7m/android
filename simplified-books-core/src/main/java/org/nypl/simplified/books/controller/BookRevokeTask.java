package org.nypl.simplified.books.controller;

import com.io7m.jfunctional.Unit;
import com.io7m.jnull.NullCheck;
import com.io7m.junreachable.UnimplementedCodeException;

import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.book_registry.BookRegistryType;
import org.nypl.simplified.http.core.HTTPType;

import java.util.concurrent.Callable;

final class BookRevokeTask implements Callable<Unit> {

  private final AccountType account;
  private final BookRegistryType book_registry;
  private final HTTPType http;

  BookRevokeTask(
      final AccountType account,
      final BookRegistryType book_registry,
      final HTTPType http) {

    this.account =
        NullCheck.notNull(account, "account");
    this.book_registry =
        NullCheck.notNull(book_registry, "book_registry");
    this.http =
        NullCheck.notNull(http, "http");
  }

  @Override
  public Unit call() throws Exception {
    throw new UnimplementedCodeException();
  }
}
