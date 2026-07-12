package com.bookloop.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bookloop.app.R;
import com.bookloop.app.models.Book;
import com.bumptech.glide.Glide;

import java.util.List;

public class BookAdapter extends RecyclerView.Adapter<BookAdapter.BookViewHolder> {

    public interface OnBookClickListener {
        void onBookClick(Book book);
    }

    private final List<Book> books;
    private final OnBookClickListener listener;

    public BookAdapter(List<Book> books, OnBookClickListener listener) {
        this.books = books;
        this.listener = listener;
    }

    @NonNull
    @Override
    public BookViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_book, parent, false);
        return new BookViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BookViewHolder holder, int position) {
        Book book = books.get(position);
        holder.bind(book);
        holder.itemView.setOnClickListener(v -> listener.onBookClick(book));
    }

    @Override
    public int getItemCount() { return books.size(); }

    static class BookViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivCover;
        private final TextView tvTitle, tvSubject, tvEdition,
                tvCondition, tvSellingPrice, tvStatus, tvSellerName;

        BookViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCover        = itemView.findViewById(R.id.ivBookCover);
            tvTitle        = itemView.findViewById(R.id.tvTitle);
            tvSubject      = itemView.findViewById(R.id.tvSubject);
            tvEdition      = itemView.findViewById(R.id.tvEdition);
            tvCondition    = itemView.findViewById(R.id.tvCondition);
            tvSellingPrice = itemView.findViewById(R.id.tvSellingPrice);
            tvStatus       = itemView.findViewById(R.id.tvStatus);
            tvSellerName   = itemView.findViewById(R.id.tvSellerName);
        }

        void bind(Book book) {
            // ── Text fields — null-safe ──────────────────────────────────────
            tvTitle.setText(book.getTitle() != null ? book.getTitle() : "Untitled");
            tvSubject.setText(book.getSubject() != null ? book.getSubject() : "");
            tvEdition.setText(book.getEdition() != null && !book.getEdition().isEmpty()
                    ? book.getEdition() : "");
            tvSellingPrice.setText("Rs. " + String.format("%.0f", book.getSellingPrice()));
            tvSellerName.setText("by " + (book.getSellerName() != null ? book.getSellerName() : "—"));

            // ── Status badge ─────────────────────────────────────────────────
            // Guard against null OR empty string — both would crash substring()
            String status = (book.getStatus() != null && !book.getStatus().isEmpty())
                    ? book.getStatus() : "available";
            tvStatus.setText(status.substring(0, 1).toUpperCase() + status.substring(1));

            // ── Condition badge — switch on null throws NPE in Java ───────────
            String condition = (book.getCondition() != null && !book.getCondition().isEmpty())
                    ? book.getCondition() : "Poor";
            tvCondition.setText(condition);

            int condBg;
            switch (condition) {
                case "Excellent": condBg = R.drawable.badge_excellent; break;
                case "Good":      condBg = R.drawable.badge_good;      break;
                case "Fair":      condBg = R.drawable.badge_fair;      break;
                default:          condBg = R.drawable.badge_poor;      break;
            }
            tvCondition.setBackgroundResource(condBg);

            // ── Status badge colour ──────────────────────────────────────────
            int statusBg;
            switch (status) {
                case "reserved": statusBg = R.drawable.badge_reserved; break;
                case "sold":     statusBg = R.drawable.badge_sold;     break;
                default:         statusBg = R.drawable.badge_available; break;
            }
            tvStatus.setBackgroundResource(statusBg);

            // ── Book cover image ─────────────────────────────────────────────
            if (book.getImageUrl() != null && !book.getImageUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(book.getImageUrl())
                        .placeholder(R.drawable.ic_book_placeholder)
                        .centerCrop()
                        .into(ivCover);
            } else {
                ivCover.setImageResource(R.drawable.ic_book_placeholder);
            }
        }
    }
}
