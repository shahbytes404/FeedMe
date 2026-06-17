import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FeedCard } from './feed-card';

describe('FeedCard', () => {
  let component: FeedCard;
  let fixture: ComponentFixture<FeedCard>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FeedCard],
    }).compileComponents();

    fixture = TestBed.createComponent(FeedCard);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
